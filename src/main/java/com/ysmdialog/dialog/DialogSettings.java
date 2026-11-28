package com.ysmdialog.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ysmdialog.YsmDialogMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组级设置：config/ysmdialog/settings.json。
 * 只放"脚本里没写时用的默认值"和贴图相关的安全阀，脚本本身仍以每个 json 为准。
 */
public final class DialogSettings {

    private static final int DEFAULT_MAX_TEXTURE_KB = 4096;

    private static int defaultCooldown = 0;
    private static boolean defaultBlockMovement = false;
    private static double defaultEndRadius = 0;
    private static int maxTextureKb = DEFAULT_MAX_TEXTURE_KB;
    private static String portraitsDir = "textures/portraits";
    private static String backgroundsDir = "textures/backgrounds";

    private DialogSettings() {
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("ysmdialog");
    }

    public static Path file() {
        return root().resolve("settings.json");
    }

    public static synchronized void load() {
        reset();
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            YsmDialogMod.LOG.error("[YsmDialog] 无法创建配置目录", e);
            return;
        }
        if (!Files.exists(path)) {
            writeDefault();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("defaultCooldown")) {
                defaultCooldown = Math.max(0, root.get("defaultCooldown").getAsInt());
            }
            if (root.has("defaultBlockMovement")) {
                defaultBlockMovement = root.get("defaultBlockMovement").getAsBoolean();
            }
            if (root.has("defaultEndRadius")) {
                defaultEndRadius = Math.max(0, root.get("defaultEndRadius").getAsDouble());
            }
            if (root.has("maxTextureKB")) {
                maxTextureKb = Math.max(64, root.get("maxTextureKB").getAsInt());
            }
            if (root.has("portraitsDir") && root.get("portraitsDir").isJsonPrimitive()) {
                portraitsDir = root.get("portraitsDir").getAsString();
            }
            if (root.has("backgroundsDir") && root.get("backgroundsDir").isJsonPrimitive()) {
                backgroundsDir = root.get("backgroundsDir").getAsString();
            }
            YsmDialogMod.LOG.info("[YsmDialog] 设置已加载：默认冷却={}s，默认限制走动={}，默认半径={}，贴图上限={}KB",
                    defaultCooldown, defaultBlockMovement, defaultEndRadius, maxTextureKb);
        } catch (Exception e) {
            YsmDialogMod.LOG.error("[YsmDialog] settings.json 解析失败，使用默认值 {}", path, e);
        }
    }

    private static void reset() {
        defaultCooldown = 0;
        defaultBlockMovement = false;
        defaultEndRadius = 0;
        maxTextureKb = DEFAULT_MAX_TEXTURE_KB;
        portraitsDir = "textures/portraits";
        backgroundsDir = "textures/backgrounds";
    }

    private static void writeDefault() {
        String json = """
                {
                  "_说明": "脚本里没写 cooldown / blockMovement / endRadius 时用的默认值；0 / false 表示不启用。",
                  "defaultCooldown": 0,
                  "defaultBlockMovement": false,
                  "defaultEndRadius": 0,
                  "_贴图说明": "portraitsDir / backgroundsDir 是相对 config/ysmdialog 的子目录，可自行改名；maxTextureKB 是单张贴图上限（超过会被拒绝下发）。",
                  "portraitsDir": "textures/portraits",
                  "backgroundsDir": "textures/backgrounds",
                  "maxTextureKB": 4096
                }
                """;
        try {
            Files.writeString(file(), json, StandardCharsets.UTF_8);
            YsmDialogMod.LOG.info("[YsmDialog] 已生成默认设置 {}", file());
        } catch (Exception e) {
            YsmDialogMod.LOG.warn("[YsmDialog] 写入默认设置失败", e);
        }
    }

    public static int defaultCooldown() {
        return defaultCooldown;
    }

    public static boolean defaultBlockMovement() {
        return defaultBlockMovement;
    }

    public static double defaultEndRadius() {
        return defaultEndRadius;
    }

    public static int maxTextureKb() {
        return maxTextureKb;
    }

    public static Path portraitsRoot() {
        return root().resolve(portraitsDir);
    }

    public static Path backgroundsRoot() {
        return root().resolve(backgroundsDir);
    }
}
