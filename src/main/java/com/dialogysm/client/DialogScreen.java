package com.dialogysm.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.dialogysm.DialogYSMMod;
import com.dialogysm.dialog.BackgroundSpec;
import com.dialogysm.dialog.DialogNode;
import com.dialogysm.dialog.DialogOption;
import com.dialogysm.dialog.DialogScript;
import com.dialogysm.dialog.ItemSpec;
import com.dialogysm.dialog.PortraitSpec;
import com.dialogysm.dialog.TextUtil;
import com.dialogysm.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉小说风格对话框。
 * 布局：立绘（左/中/右）+ 底部对话框（说话者铭牌、打字机正文、继续提示）+ 悬浮选项按钮。
 * 推进逻辑：点击/空格 → 打完字；再点 → 下一节点；有选项时必须选。
 */
public class DialogScreen extends Screen {

    private static final int LINE_H = 11;
    private static final int MAX_LINES = 7;
    /** 打字机速度（字/秒） */
    private static final float CHARS_PER_SECOND = 34f;
    /** 面板配色 */
    private static final int C_PANEL_TOP = 0xF0141A26;
    private static final int C_PANEL_BOTTOM = 0xF00A0E16;
    private static final int C_BORDER = 0xB03A4560;
    private static final int C_ACCENT = 0xFFC9A45C;
    private static final int C_TEXT = 0xFFEDEFF2;
    private static final int C_TEXT_DIM = 0xFF9AA3B2;

    private final String scriptId;
    private final DialogScript script;
    private final Component speakerName;
    private final int speakerEntityId;

    private DialogNode node;
    private BackgroundSpec background;

    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    private long openTime;
    private long nodeTime;
    private long backgroundTime;
    private boolean skipTyping;
    private boolean initialized;
    private boolean closed;

    private List<FormattedCharSequence> lines;
    private int totalChars = -1;

    private final List<int[]> optionRects = new ArrayList<>();
    /** 选项区滚动偏移（像素）：GUI 缩放较大 / 选项多时，超出可视区靠滚轮上下翻 */
    private int optionScroll;

    public DialogScreen(String scriptId, DialogScript script, Component speakerName, int speakerEntityId) {
        super(Component.literal(script.title == null ? "对话" : script.title));
        this.scriptId = scriptId;
        this.script = script;
        this.speakerName = speakerName;
        this.speakerEntityId = speakerEntityId;
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void init() {
        // 布局：底部居中面板
        this.boxW = Math.min(620, this.width - 40);
        this.boxH = 118;
        this.boxX = (this.width - boxW) / 2;
        this.boxY = this.height - boxH - 16;

        if (!initialized) {
            initialized = true;
            this.openTime = System.currentTimeMillis();
            enterNode(script.firstNode());
        }
        this.lines = null;
        this.optionRects.clear();
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停：世界继续跑（VN 演出、NPC 动画都不中断）
        return false;
    }

    @Override
    public void onClose() {
        if (!closed) {
            closed = true;
            Net.sendEnd(scriptId);
        }
        super.onClose();
    }

    /** 当前显示的脚本 id（服务端下发关闭包时用来比对） */
    public String scriptId() {
        return scriptId;
    }

    /** 服务端主动关闭：不再回传 end 包，直接关界面 */
    public void closeFromServer() {
        closed = true;
        Minecraft.getInstance().setScreen(null);
    }

    // ---------------------------------------------------------------- 节点推进

    private void enterNode(DialogNode next) {
        this.node = next;
        this.nodeTime = System.currentTimeMillis();
        this.skipTyping = false;
        this.lines = null;
        this.totalChars = -1;
        this.optionRects.clear();
        this.optionScroll = 0;

        if (next == null) {
            closeWithEnd();
            return;
        }
        if (next.backgroundImage != null && next.backgroundImage.path != null && !next.backgroundImage.path.isBlank()) {
            this.background = next.backgroundImage;
            this.backgroundTime = System.currentTimeMillis();
        }
        // 进入节点就执行该节点的 command / plugin_command（服务端校验白名单后执行）
        for (String cmd : TextUtil.stringList(next.command)) {
            Net.sendCommand(scriptId, cmd);
        }
        for (String cmd : TextUtil.stringList(next.pluginCommand)) {
            Net.sendPluginCommand(scriptId, cmd);
        }
    }

    private void advance() {
        if (node == null) {
            closeWithEnd();
            return;
        }
        if (!textDone()) {
            if (node.skipAllowed()) {
                skipTyping = true;
            }
            return;
        }
        if (!visibleOptions().isEmpty()) {
            // 有选项时必须选一个
            return;
        }
        if (node.endsDialog()) {
            closeWithEnd();
            return;
        }
        DialogNode next = script.next(node);
        if (next == null) {
            closeWithEnd();
        } else {
            enterNode(next);
        }
    }

    private void choose(int index) {
        List<DialogOption> opts = visibleOptions();
        if (index < 0 || index >= opts.size()) {
            return;
        }
        DialogOption opt = opts.get(index);
        for (String cmd : TextUtil.stringList(opt.command)) {
            Net.sendCommand(scriptId, cmd);
        }
        for (String cmd : TextUtil.stringList(opt.pluginCommand)) {
            Net.sendPluginCommand(scriptId, cmd);
        }
        // close=true：执行完命令直接结束对话（常用于"点选项 → 开菜单/传送"这类一跳就走的场景）
        if (opt.close != null && opt.close) {
            closeWithEnd();
            return;
        }
        String target = opt.target;
        if (target == null || target.isBlank()) {
            advance();
            return;
        }
        if ("end".equalsIgnoreCase(target)) {
            closeWithEnd();
            return;
        }
        DialogNode next = script.find(target);
        if (next == null) {
            DialogYSMMod.LOG.warn("[DialogYSM] 选项 target 找不到节点: {}", target);
            closeWithEnd();
            return;
        }
        enterNode(next);
    }

    private void closeWithEnd() {
        if (closed) {
            return;
        }
        closed = true;
        Net.sendEnd(scriptId);
        Minecraft.getInstance().setScreen(null);
    }

    private List<DialogOption> visibleOptions() {
        if (node == null || node.options == null || node.options.isEmpty()) {
            return List.of();
        }
        return node.options;
    }

    // ---------------------------------------------------------------- 文本

    private String playerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "旅行者" : mc.player.getGameProfile().getName();
    }

    private Component nodeText() {
        if (node == null || node.text == null) {
            return Component.empty();
        }
        return TextUtil.replace(TextUtil.fromJson(node.text), "@i", playerName());
    }

    private Component nodeSpeaker() {
        if (node != null && node.speaker != null) {
            Component s = TextUtil.replace(TextUtil.fromJson(node.speaker), "@i", playerName());
            if (!s.getString().isBlank()) {
                return s;
            }
        }
        return speakerName;
    }

    private List<FormattedCharSequence> ensureLines() {
        if (lines == null) {
            lines = this.font.split(nodeText(), this.boxW - 46);
            totalChars = 0;
            for (FormattedCharSequence line : lines) {
                totalChars += countChars(line);
            }
        }
        return lines;
    }

    private int typedChars() {
        if (skipTyping) {
            return totalChars;
        }
        long elapsed = System.currentTimeMillis() - nodeTime;
        return Math.min(totalChars, (int) (elapsed * CHARS_PER_SECOND / 1000.0));
    }

    private boolean textDone() {
        ensureLines();
        return typedChars() >= totalChars;
    }

    private static int countChars(FormattedCharSequence seq) {
        int[] n = {0};
        seq.accept((index, style, codepoint) -> {
            n[0]++;
            return true;
        });
        return n[0];
    }

    private static FormattedCharSequence cut(FormattedCharSequence seq, int limit) {
        return sink -> {
            int[] n = {0};
            return seq.accept((index, style, codepoint) -> {
                if (n[0] >= limit) {
                    return false;
                }
                n[0]++;
                return sink.accept(index, style, codepoint);
            });
        };
    }

    /** 按已显示字数裁剪出当前该画的行 */
    private List<FormattedCharSequence> visibleLines() {
        ensureLines();
        int budget = typedChars();
        List<FormattedCharSequence> out = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            int len = countChars(line);
            if (budget <= 0) {
                break;
            }
            if (len <= budget) {
                out.add(line);
                budget -= len;
            } else {
                out.add(cut(line, budget));
                budget = 0;
                break;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        long now = System.currentTimeMillis();
        float appear = Math.min(1.0f, (now - openTime) / 220.0f);

        renderCg(g, now, appear);
        renderPortraits(g, now);
        renderBox(g, now, appear);
        renderItems(g);
        if (textDone()) {
            renderOptions(g, mouseX, mouseY, appear);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCg(GuiGraphics g, long now, float appear) {
        if (background == null || background.path == null || background.path.isBlank()) {
            return;
        }
        ResourceLocation rl = backgroundLocation(background.path);
        int[] size = textureSize(rl);
        if (size == null) {
            return;
        }
        float fade = Math.min(1.0f, (now - backgroundTime) / 320.0f);
        int x;
        int y;
        int w;
        int h;
        if ("FILL".equalsIgnoreCase(background.renderOption)) {
            x = 0;
            y = 0;
            w = this.width;
            h = this.height;
        } else {
            float scale = Math.min(this.width / (float) size[0], this.height / (float) size[1]);
            w = (int) (size[0] * scale);
            h = (int) (size[1] * scale);
            x = (this.width - w) / 2;
            y = (this.height - h) / 2;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fade);
        g.blit(rl, x, y, w, h, 0f, 0f, size[0], size[1], size[0], size[1]);
        g.flush();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        // 叠一层黑纱，保证文字可读
        g.fill(0, 0, this.width, this.height, 0x40000000);
    }

    private void renderPortraits(GuiGraphics g, long now) {
        if (node == null || node.portraits == null || node.portraits.isEmpty()) {
            return;
        }
        float anim = Math.min(1.0f, (now - nodeTime) / 260.0f);
        for (PortraitSpec spec : node.portraits) {
            if (spec == null || spec.path == null || spec.path.isBlank()) {
                continue;
            }
            ResourceLocation rl = portraitLocation(spec.path);
            int[] size = textureSize(rl);
            if (size == null) {
                continue;
            }
            float scale = Math.max(0.2f, Math.min(3.0f, spec.size));
            int targetH = (int) ((boxY - 8) * 0.96f * scale);
            int targetW = (int) (targetH * (size[0] / (float) size[1]));
            int baseY = boxY + 26 - targetH;
            int baseX;
            switch (spec.position == null ? "RIGHT" : spec.position.toUpperCase()) {
                case "LEFT" -> baseX = 36;
                case "CENTER" -> baseX = (this.width - targetW) / 2;
                default -> baseX = this.width - targetW - 36;
            }
            // 入场动画
            int offsetX = 0;
            float alpha = anim;
            switch (spec.animationType == null ? "NONE" : spec.animationType.toUpperCase()) {
                case "SLIDE_LEFT" -> offsetX = (int) (-60 * (1 - anim));
                case "SLIDE_RIGHT" -> offsetX = (int) (60 * (1 - anim));
                case "SLIDE_UP" -> baseY += (int) (50 * (1 - anim));
                default -> {
                }
            }
            float brightness = Math.max(0.0f, Math.min(1.0f, spec.brightness));
            float finalAlpha = alpha * Math.max(0.0f, Math.min(1.0f, spec.alpha));

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(brightness, brightness, brightness, finalAlpha);
            g.blit(rl, baseX + offsetX, baseY, targetW, targetH,
                    0f, 0f, size[0], size[1], size[0], size[1]);
            // 立刻落地：避免着色器颜色把同批次的其他元素一起染色
            g.flush();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }

    private void renderBox(GuiGraphics g, long now, float appear) {
        int slide = (int) ((1 - appear) * 14);
        int bx = boxX;
        int by = boxY + slide;

        // 外阴影 + 边框 + 渐变面板
        g.fill(bx - 4, by - 4, bx + boxW + 4, by + boxH + 4, 0x55000000);
        g.fill(bx - 2, by - 2, bx + boxW + 2, by + boxH + 2, C_BORDER);
        g.fillGradient(bx, by, bx + boxW, by + boxH, C_PANEL_TOP, C_PANEL_BOTTOM);
        g.fill(bx, by, bx + boxW, by + 1, C_ACCENT);
        g.fill(bx, by + boxH - 1, bx + boxW, by + boxH, 0x60C9A45C);
        // 左上角小装饰
        g.fill(bx + 12, by + 10, bx + 24, by + 12, C_ACCENT);

        // 说话者铭牌
        Component speaker = nodeSpeaker();
        if (speaker != null && !speaker.getString().isBlank()) {
            int nw = this.font.width(speaker) + 30;
            g.fill(bx + 12, by - 28, bx + 12 + nw, by - 2, 0xE0141A26);
            g.fill(bx + 12, by - 28, bx + 12 + nw, by - 27, C_ACCENT);
            g.drawString(this.font, speaker, bx + 26, by - 21, 0xFFE8D8B0, false);
        }

        // 正文（打字机）
        List<FormattedCharSequence> shown = visibleLines();
        int ty = by + 26;
        for (int i = 0; i < shown.size() && i < MAX_LINES; i++) {
            g.drawString(this.font, shown.get(i), bx + 20, ty + i * LINE_H, C_TEXT, false);
        }

        // 右下角：继续提示 + 呼吸箭头
        boolean done = textDone();
        boolean hasOpts = !visibleOptions().isEmpty();
        String hint = done ? (hasOpts ? "请选择" : "点击继续") : "点击跳过";
        int hw = this.font.width(hint);
        g.drawString(this.font, hint, bx + boxW - 22 - hw, by + boxH - 18, C_TEXT_DIM, false);
        if (done && !hasOpts && ((now / 480) % 2 == 0)) {
            g.drawString(this.font, "▼", bx + boxW - 18, by + boxH - 18, C_ACCENT, false);
        }
    }

    private void renderItems(GuiGraphics g) {
        if (node == null || node.displayItems == null || node.displayItems.isEmpty()) {
            return;
        }
        int n = Math.min(6, node.displayItems.size());
        int x = boxX + boxW - 24 - (n - 1) * 24;
        int y = boxY - 40;
        for (int i = 0; i < n; i++) {
            ItemStack stack = itemStack(node.displayItems.get(i));
            int ix = x + i * 24;
            g.fill(ix - 2, y - 2, ix + 20, y + 20, 0x90000000);
            g.fill(ix - 2, y - 2, ix + 20, y - 1, 0x60C9A45C);
            if (!stack.isEmpty()) {
                g.renderItem(stack, ix, y);
                g.renderItemDecorations(this.font, stack, ix, y);
            }
        }
    }

    /**
     * 选项区几何（面板上方到屏幕顶部之间）。
     * 返回 {rowH, gap, count, areaTop, areaBottom, contentH, maxScroll}。
     * 放不下时先压间距、再压行高；压到最小行高仍放不下才允许滚动。
     */
    private int[] optionLayout(int total) {
        int count = Math.min(total, 9);
        int areaBottom = boxY - 12;
        int areaTop = 6;
        int avail = Math.max(0, areaBottom - areaTop);
        int rowH = 22;
        int gap = 6;
        int contentH = count * rowH + Math.max(0, count - 1) * gap;
        if (contentH > avail && count > 0) {
            gap = 2;
            rowH = Math.max(12, (avail - Math.max(0, count - 1) * gap) / count);
            contentH = count * rowH + Math.max(0, count - 1) * gap;
        }
        int maxScroll = Math.max(0, contentH - avail);
        return new int[]{rowH, gap, count, areaTop, areaBottom, contentH, maxScroll};
    }

    private void renderOptions(GuiGraphics g, int mouseX, int mouseY, float appear) {
        List<DialogOption> opts = visibleOptions();
        optionRects.clear();
        if (opts.isEmpty()) {
            return;
        }
        int[] l = optionLayout(opts.size());
        int rowH = l[0];
        int gap = l[1];
        int count = l[2];
        int areaTop = l[3];
        int areaBottom = l[4];
        int contentH = l[5];
        int maxScroll = l[6];
        optionScroll = Mth.clamp(optionScroll, 0, maxScroll);

        int ow = Math.min(420, boxW - 30);
        int ox = (this.width - ow) / 2;
        // 放得下就贴着面板底部往上排；放不下就顶部对齐 + 滚轮翻页（默认从第 1 项开始）
        int startY = maxScroll > 0 ? areaTop - optionScroll : areaBottom - contentH;

        g.enableScissor(ox - 3, areaTop - 1, ox + ow + 3, areaBottom + 1);
        for (int i = 0; i < count; i++) {
            int ry = startY + i * (rowH + gap);
            if (ry + rowH < areaTop || ry > areaBottom) {
                continue;
            }
            boolean hover = mouseX >= ox && mouseX <= ox + ow && mouseY >= ry && mouseY <= ry + rowH;
            int bg = hover ? 0xF01F2A3E : 0xC8141B28;
            g.fill(ox - 2, ry - 2, ox + ow + 2, ry + rowH + 2, 0x80000000);
            g.fill(ox, ry, ox + ow, ry + rowH, bg);
            g.fill(ox, ry, ox + 3, ry + rowH, hover ? C_ACCENT : 0x80C9A45C);
            if (hover) {
                g.fill(ox, ry + rowH - 1, ox + ow, ry + rowH, C_ACCENT);
            }
            Component text = TextUtil.replace(TextUtil.fromJson(opts.get(i).text), "@i", playerName());
            int tw = this.font.width(text);
            int tx = ox + (ow - tw) / 2;
            int ty = ry + (rowH - 8) / 2;
            g.drawString(this.font, text, tx, ty, hover ? 0xFFFFF0CC : C_TEXT, false);
            // 序号
            g.drawString(this.font, String.valueOf(i + 1), ox + 8, ty, C_TEXT_DIM, false);
            // 只有完整落在可视区内的行才登记点击区域，避免被面板/屏幕边缘半截遮挡还能点
            // 第 5 个元素存真实选项下标（滚动时可见行与下标不再一一对应，不能按下标数推算）
            if (ry >= areaTop && ry + rowH <= areaBottom) {
                optionRects.add(new int[]{ox, ry, ow, rowH, i});
            }
        }
        g.disableScissor();

        // 滚动提示条：有溢出时在右侧画轨道+滑块，并在上下两端各画一个小箭头
        if (maxScroll > 0) {
            int trackX = ox + ow + 6;
            int trackH = areaBottom - areaTop;
            g.fill(trackX, areaTop, trackX + 3, areaBottom, 0x40FFFFFF);
            int thumbH = Math.max(14, trackH * trackH / Math.max(1, contentH));
            int thumbY = areaTop + (trackH - thumbH) * optionScroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, C_ACCENT);
            if (optionScroll > 0) {
                g.drawString(this.font, "▲", ox + ow - 10, areaTop + 1, C_TEXT_DIM, false);
            }
            if (optionScroll < maxScroll) {
                g.drawString(this.font, "▼", ox + ow - 10, areaBottom - 9, C_TEXT_DIM, false);
            }
        }
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 选项溢出时滚轮上下翻（一次一格 = 行高+间距），其余情况交给父类
        List<DialogOption> opts = visibleOptions();
        if (!opts.isEmpty()) {
            int[] l = optionLayout(opts.size());
            if (l[6] > 0) {
                int step = Math.max(10, l[0] + l[1]);
                optionScroll = Mth.clamp(optionScroll - (int) Math.round(delta * step), 0, l[6]);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!textDone()) {
            if (node != null && node.skipAllowed()) {
                skipTyping = true;
            }
            return true;
        }
        List<DialogOption> opts = visibleOptions();
        if (!opts.isEmpty()) {
            for (int[] r : optionRects) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    choose(r[4]);
                    return true;
                }
            }
            return true; // 有选项时点空白不推进，避免误触
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (script.closeAllowed()) {
                closeWithEnd();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // 选项界面按空格=选第 1 项，更顺手
            if (textDone() && !visibleOptions().isEmpty()) {
                choose(0);
            } else {
                advance();
            }
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            List<DialogOption> opts = visibleOptions();
            int idx = keyCode - GLFW.GLFW_KEY_1;
            if (textDone() && idx < opts.size()) {
                choose(idx);
                return true;
            }
            // 数字键在打字阶段也用来快速跳完
            if (!textDone() && node != null && node.skipAllowed()) {
                skipTyping = true;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- 资源工具

    private static ResourceLocation portraitLocation(String path) {
        if (path.contains(":")) {
            return new ResourceLocation(path);
        }
        if (path.startsWith("textures/")) {
            return new ResourceLocation(DialogYSMMod.MODID, path);
        }
        return new ResourceLocation(DialogYSMMod.MODID, "textures/portraits/" + path);
    }

    private static ResourceLocation backgroundLocation(String path) {
        if (path.contains(":")) {
            return new ResourceLocation(path);
        }
        if (path.startsWith("textures/")) {
            return new ResourceLocation(DialogYSMMod.MODID, path);
        }
        return new ResourceLocation(DialogYSMMod.MODID, "textures/backgrounds/" + path);
    }

    /** 贴图尺寸缓存（按资源路径读 PNG 头，避免依赖 AbstractTexture 的尺寸 API） */
    private static final java.util.Map<ResourceLocation, int[]> TEX_SIZE_CACHE = new java.util.HashMap<>();
    private static final int[] MISSING = new int[]{0, 0};

    /** 配置目录下发的动态贴图：直接写入已知尺寸，省一次解码 */
    public static void putTextureSize(ResourceLocation rl, int width, int height) {
        TEX_SIZE_CACHE.put(rl, new int[]{width, height});
    }

    private static int[] textureSize(ResourceLocation rl) {
        int[] cached = TEX_SIZE_CACHE.get(rl);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        try (java.io.InputStream in = Minecraft.getInstance().getResourceManager()
                .getResource(rl).orElseThrow().open()) {
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(in);
            int[] size = new int[]{image.getWidth(), image.getHeight()};
            image.close();
            TEX_SIZE_CACHE.put(rl, size);
            return size;
        } catch (Exception e) {
            TEX_SIZE_CACHE.put(rl, MISSING);
            return null;
        }
    }

    private static ItemStack itemStack(ItemSpec spec) {
        if (spec == null || spec.item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(spec.item)));
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack.setCount(Math.max(1, Math.min(64, spec.count)));
        if (spec.nbt != null && !spec.nbt.isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(spec.nbt));
            } catch (Exception ignored) {
            }
        }
        return stack;
    }
}
