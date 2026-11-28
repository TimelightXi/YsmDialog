package com.dialogysm.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dialogysm.DialogYSMMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPC → 对话脚本 的绑定表。
 * 存在存档目录 <world>/dialogysm/bindings.json，形如：
 * { "entities": { "<uuid>": "quest_intro" }, "tags": { "quest_giver": "quest_intro" } }
 * 用 uuid 绑定的好处：Citizens 重建 NPC 后小概率变 uuid，需要重新 bind 一次即可。
 */
public final class DialogBindings {

    private static final Map<String, String> BY_UUID = new HashMap<>();
    private static final Map<String, String> BY_TAG = new HashMap<>();
    private static Path file;
    private static boolean dirty = false;

    private DialogBindings() {
    }

    public static Path fileFor(MinecraftServer server) {
        if (file == null) {
            file = server.getWorldPath(LevelResource.ROOT).resolve("dialogysm").resolve("bindings.json");
        }
        return file;
    }

    public static synchronized void load(MinecraftServer server) {
        file = fileFor(server);
        BY_UUID.clear();
        BY_TAG.clear();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            readMap(root, "entities", BY_UUID);
            readMap(root, "tags", BY_TAG);
            DialogYSMMod.LOG.info("[DialogYSM] 已加载 {} 个实体绑定 / {} 个 tag 绑定", BY_UUID.size(), BY_TAG.size());
        } catch (Exception e) {
            DialogYSMMod.LOG.error("[DialogYSM] 绑定表读取失败 {}", file, e);
        }
    }

    private static void readMap(JsonObject root, String key, Map<String, String> target) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject(key).entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                target.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    public static synchronized void save(MinecraftServer server) {
        Path path = fileFor(server);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonObject entities = new JsonObject();
            BY_UUID.forEach(entities::addProperty);
            JsonObject tags = new JsonObject();
            BY_TAG.forEach(tags::addProperty);
            root.add("entities", entities);
            root.add("tags", tags);
            Files.writeString(path, DialogScript.GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            DialogYSMMod.LOG.error("[DialogYSM] 绑定表保存失败 {}", path, e);
        }
    }

    public static synchronized void bindEntity(UUID uuid, String scriptId, MinecraftServer server) {
        BY_UUID.put(uuid.toString(), scriptId);
        save(server);
    }

    public static synchronized void unbindEntity(UUID uuid, MinecraftServer server) {
        BY_UUID.remove(uuid.toString());
        save(server);
    }

    public static synchronized void bindTag(String tag, String scriptId, MinecraftServer server) {
        BY_TAG.put(tag, scriptId);
        save(server);
    }

    /** 按实体 uuid 找脚本；找不到再按实体的 scoreboard tag 找 */
    public static synchronized String scriptFor(net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        String byUuid = BY_UUID.get(entity.getUUID().toString());
        if (byUuid != null) {
            return byUuid;
        }
        for (String tag : entity.getTags()) {
            String byTag = BY_TAG.get(tag);
            if (byTag != null) {
                return byTag;
            }
        }
        return null;
    }

    public static synchronized Map<String, String> snapshot() {
        Map<String, String> all = new HashMap<>(BY_UUID);
        BY_TAG.forEach((k, v) -> all.put("tag:" + k, v));
        return all;
    }
}
