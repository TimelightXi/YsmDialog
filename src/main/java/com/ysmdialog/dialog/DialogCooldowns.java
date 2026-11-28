package com.ysmdialog.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ysmdialog.YsmDialogMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 对话冷却表（按 玩家 + 脚本 计）。
 * 存在存档目录 &lt;world&gt;/ysmdialog/cooldowns.json，形如：
 * { "&lt;uuid&gt;|newbie": 1770896093647 }   // 值 = 解禁时间（毫秒时间戳）
 */
public final class DialogCooldowns {

    private static final Map<String, Long> UNTIL = new HashMap<>();
    private static Path file;
    private static MinecraftServer server;

    private DialogCooldowns() {
    }

    private static String key(UUID player, String scriptId) {
        return player + "|" + scriptId;
    }

    public static synchronized void load(MinecraftServer s) {
        server = s;
        file = s.getWorldPath(LevelResource.ROOT).resolve("ysmdialog").resolve("cooldowns.json");
        UNTIL.clear();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            long now = System.currentTimeMillis();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                long until = e.getValue().getAsLong();
                if (until > now) {
                    UNTIL.put(e.getKey(), until);
                }
            }
            YsmDialogMod.LOG.info("[YsmDialog] 已加载 {} 条冷却记录", UNTIL.size());
        } catch (Exception e) {
            YsmDialogMod.LOG.error("[YsmDialog] 冷却表读取失败 {}", file, e);
        }
    }

    /** 剩余冷却秒数，0 表示可以开 */
    public static synchronized long remainingSeconds(UUID player, String scriptId) {
        Long until = UNTIL.get(key(player, scriptId));
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            UNTIL.remove(key(player, scriptId));
            return 0;
        }
        return (left + 999) / 1000;
    }

    public static synchronized void mark(UUID player, String scriptId, int seconds) {
        if (seconds <= 0) {
            return;
        }
        UNTIL.put(key(player, scriptId), System.currentTimeMillis() + seconds * 1000L);
        save();
    }

    public static synchronized void clear(UUID player, String scriptId) {
        if (UNTIL.remove(key(player, scriptId)) != null) {
            save();
        }
    }

    private static void save() {
        if (server == null || file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            long now = System.currentTimeMillis();
            UNTIL.forEach((k, v) -> {
                if (v > now) {
                    root.addProperty(k, v);
                }
            });
            Files.writeString(file, DialogScript.GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            YsmDialogMod.LOG.error("[YsmDialog] 冷却表保存失败 {}", file, e);
        }
    }
}
