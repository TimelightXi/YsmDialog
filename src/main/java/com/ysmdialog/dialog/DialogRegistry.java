package com.ysmdialog.dialog;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import com.ysmdialog.YsmDialogMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 服务端脚本仓库：
 * 1) 主目录：config/ysmdialog/dialogs/*.json（可直接改、改完 /ysmdialog reload 生效）
 * 2) 兼容数据包：data/ysmdialog/dialogs/*.json（打进整合包分发时用）
 * 3) 首次启动会从模组内置资源解出示例脚本到 config 目录
 */
public final class DialogRegistry {

    private static final Map<String, String> RAW = new LinkedHashMap<>();

    /** 模组内置的示例脚本文件名 */
    private static final String[] BUNDLED = {"quest_intro.json", "npc_daily.json", "menu_hub.json"};

    private DialogRegistry() {
    }

    public static Path scriptDir() {
        return FMLPaths.CONFIGDIR.get().resolve("ysmdialog").resolve("dialogs");
    }

    public static synchronized void reload(MinecraftServer server) {
        Path dir = scriptDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            YsmDialogMod.LOG.error("[YsmDialog] 无法创建脚本目录 {}", dir, e);
        }
        extractBundledDefaults(dir);

        RAW.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                String file = p.getFileName().toString();
                String id = file.substring(0, file.length() - 5);
                try {
                    RAW.put(id, Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    YsmDialogMod.LOG.error("[YsmDialog] 读取脚本失败: {}", p, e);
                }
            }
        } catch (IOException e) {
            YsmDialogMod.LOG.error("[YsmDialog] 遍历脚本目录失败 {}", dir, e);
        }

        loadFromDatapacks(server);

        YsmDialogMod.LOG.info("[YsmDialog] 已加载 {} 个对话脚本: {}", RAW.size(), RAW.keySet());
    }

    /** 从数据包目录补充脚本（同 id 时不覆盖 config 目录） */
    private static void loadFromDatapacks(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            Map<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> found =
                    server.getResourceManager().listResources("data/ysmdialog/dialogs",
                            rl -> rl.getPath().endsWith(".json"));
            for (Map.Entry<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> e : found.entrySet()) {
                String id = e.getKey().getPath();
                id = id.substring(id.lastIndexOf('/') + 1, id.length() - 5);
                if (RAW.containsKey(id)) {
                    continue;
                }
                try (InputStream in = e.getValue().open()) {
                    RAW.put(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ex) {
            YsmDialogMod.LOG.warn("[YsmDialog] 数据包脚本读取跳过: {}", ex.toString());
        }
    }

    private static void extractBundledDefaults(Path dir) {
        for (String name : BUNDLED) {
            Path target = dir.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in = YsmDialogMod.class.getResourceAsStream("/assets/ysmdialog/scripts/" + name)) {
                if (in == null) {
                    continue;
                }
                Files.write(target, in.readAllBytes());
                YsmDialogMod.LOG.info("[YsmDialog] 已释放内置脚本 {}", target);
            } catch (IOException e) {
                YsmDialogMod.LOG.warn("[YsmDialog] 释放内置脚本失败 {}", name, e);
            }
        }
    }

    public static boolean has(String scriptId) {
        return scriptId != null && RAW.containsKey(scriptId);
    }

    /** 所有已加载脚本 id */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(RAW.keySet());
    }

    /** 取原始 JSON（未做可见性过滤） */
    public static String rawJson(String scriptId) {
        return RAW.get(scriptId);
    }

    /**
     * 取该玩家视角的脚本 JSON：把可见性条件不满足的选项删掉。
     * 节点不做删除，防止 next 链断掉。
     */
    public static String jsonForPlayer(String scriptId, ServerPlayer player) {
        String raw = RAW.get(scriptId);
        if (raw == null) {
            return null;
        }
        try {
            DialogScript script = DialogScript.parse(raw);
            if (script == null) {
                return raw;
            }
            if (script.entries != null) {
                for (DialogNode node : script.entries) {
                    if (node == null || node.options == null || node.options.isEmpty()) {
                        continue;
                    }
                    node.options.removeIf(opt -> opt.visibilityCommand != null
                            && !opt.visibilityCommand.isBlank()
                            && !test(player, opt.visibilityCommand));
                }
            }
            return script.toJson();
        } catch (Exception ex) {
            YsmDialogMod.LOG.error("[YsmDialog] 脚本过滤失败 {}，直接下发原文", scriptId, ex);
            return raw;
        }
    }

    /** 执行 "execute if ..." 形式的条件命令，返回结果是否 > 0 */
    private static boolean test(ServerPlayer player, String condition) {
        if (player == null) {
            return true;
        }
        try {
            return player.getServer().getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), condition) > 0;
        } catch (Exception ex) {
            YsmDialogMod.LOG.warn("[YsmDialog] 条件命令执行失败: {}", condition);
            return false;
        }
    }
}
