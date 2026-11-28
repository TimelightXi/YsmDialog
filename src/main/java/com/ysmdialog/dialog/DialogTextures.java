package com.ysmdialog.dialog;

import com.ysmdialog.YsmDialogMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 配置目录里的贴图（config/ysmdialog/textures/...）。
 * <p>
 * 解析顺序（服务端）：先用这里的 PNG，命中就把脚本里的 path 改写成
 * {@code ysmdialog:custom/<hash>} 并随对话下发；没命中则保留原 path，
 * 客户端按原有规则去读模组 / 其他模组的资源（兼容 jar 内素材）。
 */
public final class DialogTextures {

    /** 一张要下发的贴图 */
    public record Payload(String rl, Path file) {
    }

    private DialogTextures() {
    }

    /** 按相对路径找文件；含冒号（外部命名空间）、含 .. 的不接管 */
    private static Path resolve(Path base, String raw) {
        if (raw == null || raw.isBlank() || base == null) {
            return null;
        }
        if (raw.contains(":")) {
            return null;
        }
        String rel = raw.replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.contains("..")) {
            return null;
        }
        // 宽容写法：既能写 "npc.png"，也能写 "portraits/npc.png"、"textures/portraits/npc.png"
        Path[] candidates = {
                base.resolve(rel),
                DialogSettings.root().resolve(rel),
                DialogSettings.root().resolve("textures").resolve(rel)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 把脚本里配置目录命中的立绘 / 背景路径改写成动态资源 id，并把文件收进 out。
     * 同一个文件多节点引用只下发一次。
     */
    public static void prepare(DialogScript script, List<Payload> out) {
        if (script == null || script.entries == null || script.entries.isEmpty()) {
            return;
        }
        int maxBytes = DialogSettings.maxTextureKb() * 1024;
        Set<String> seen = new HashSet<>();
        for (DialogNode node : script.entries) {
            if (node == null) {
                continue;
            }
            if (node.portraits != null) {
                for (PortraitSpec spec : node.portraits) {
                    if (spec == null || spec.path == null || spec.path.isBlank()) {
                        continue;
                    }
                    Path file = resolve(DialogSettings.portraitsRoot(), spec.path);
                    if (file == null) {
                        continue;
                    }
                    String rl = idFor(file, maxBytes, spec.path);
                    if (rl == null) {
                        continue;
                    }
                    spec.path = rl;
                    if (seen.add(rl)) {
                        out.add(new Payload(rl, file));
                    }
                }
            }
            BackgroundSpec bg = node.backgroundImage;
            if (bg != null && bg.path != null && !bg.path.isBlank()) {
                Path file = resolve(DialogSettings.backgroundsRoot(), bg.path);
                if (file != null) {
                    String rl = idFor(file, maxBytes, bg.path);
                    if (rl != null) {
                        bg.path = rl;
                        if (seen.add(rl)) {
                            out.add(new Payload(rl, file));
                        }
                    }
                }
            }
        }
    }

    /** 资源 id = ysmdialog:custom/<12 位哈希>，哈希含 mtime/size，改图后自动换 id */
    private static String idFor(Path file, int maxBytes, String shownPath) {
        try {
            long size = Files.size(file);
            if (size <= 0) {
                YsmDialogMod.LOG.warn("[YsmDialog] 贴图为空，跳过: {}", shownPath);
                return null;
            }
            if (size > maxBytes) {
                YsmDialogMod.LOG.warn("[YsmDialog] 贴图超过上限 {}KB，跳过: {}（{}B）",
                        maxBytes / 1024, shownPath, size);
                return null;
            }
            String seed = file.toAbsolutePath() + ":" + size + ":" + Files.getLastModifiedTime(file).toMillis();
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(seed.getBytes(StandardCharsets.UTF_8));
            return "ysmdialog:custom/" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            YsmDialogMod.LOG.warn("[YsmDialog] 计算贴图 id 失败: {}", shownPath, e);
            return null;
        }
    }

    /**
     * 首次启动释放样例：立绘 npc.png + 目录说明。已存在就跳过，不覆盖用户素材。
     */
    public static synchronized void extractDefaults() {
        try {
            Files.createDirectories(DialogSettings.portraitsRoot());
            Files.createDirectories(DialogSettings.backgroundsRoot());
        } catch (IOException e) {
            YsmDialogMod.LOG.error("[YsmDialog] 无法创建立绘/背景目录", e);
            return;
        }

        Path sample = DialogSettings.portraitsRoot().resolve("npc.png");
        if (!Files.exists(sample)) {
            try (InputStream in = YsmDialogMod.class.getResourceAsStream("/assets/ysmdialog/textures/portraits/npc.png")) {
                if (in != null) {
                    Files.write(sample, in.readAllBytes());
                    YsmDialogMod.LOG.info("[YsmDialog] 已释放示例立绘 {}", sample);
                }
            } catch (IOException e) {
                YsmDialogMod.LOG.warn("[YsmDialog] 释放示例立绘失败", e);
            }
        }

        Path readme = DialogSettings.root().resolve("textures").resolve("使用说明.txt");
        if (!Files.exists(readme)) {
            String text = """
                    YsmDialog 贴图目录
                    ===================
                    portraits/    立绘：脚本里 "path": "npc.png" 会在这里找（支持子目录，如 "town/elder.png"）
                    backgrounds/  背景大图：脚本里 background_image.path = "forest.png" 会在这里找

                    规则
                    - 只认 PNG（带 alpha 通道最好），单张默认上限 4096KB，可在 config/ysmdialog/settings.json 的 maxTextureKB 改
                    - 放好文件后执行 /ysmdialog reload，再开一次对话就会把新图下发给客户端
                    - 想用别的模组/原版贴图，直接写完整路径，如 "minecraft:textures/block/stone.png"
                      （这种写法不会走本目录，也不会被下发）
                    - 改图后资源 id 会变，客户端会重新接收，无需清缓存
                    """;
            try {
                Files.writeString(readme, text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                YsmDialogMod.LOG.warn("[YsmDialog] 写入贴图说明失败", e);
            }
        }
    }

    /** 列出当前可用立绘（给 /ysmdialog list 用） */
    public static List<String> listPortraits() {
        List<String> result = new ArrayList<>();
        Path base = DialogSettings.portraitsRoot();
        if (!Files.isDirectory(base)) {
            return result;
        }
        try (Stream<Path> stream = Files.walk(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .forEach(p -> result.add(base.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ignored) {
            // 目录读不到就当没有
        }
        result.sort(String::compareTo);
        return result;
    }
}
