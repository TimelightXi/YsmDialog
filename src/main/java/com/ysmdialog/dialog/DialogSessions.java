package com.ysmdialog.dialog;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 玩家当前进行中的对话会话。
 * 记录本次下发的脚本里出现过的命令白名单（原版命令 / 插件命令两套），
 * 服务端收到客户端请求时校验，避免客户端伪造任意命令；
 * 同时记录开对话时的位置与维度，供 blockMovement / 走动半径校验使用。
 */
public final class DialogSessions {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DialogSessions() {
    }

    public static final class Session {
        public final String scriptId;
        public final Set<String> allowed = new HashSet<>();
        public final Set<String> allowedPlugin = new HashSet<>();

        /** 开对话时的位置与维度（blockMovement / endRadius 用） */
        public final Vec3 startPos;
        public final ResourceKey<Level> dimension;
        /** 是否在对话中限制走动 */
        public final boolean blockMovement;
        /** 允许半径，&lt;=0 表示不限 */
        public final double radius;

        Session(String scriptId, Vec3 startPos, ResourceKey<Level> dimension, boolean blockMovement, double radius) {
            this.scriptId = scriptId;
            this.startPos = startPos;
            this.dimension = dimension;
            this.blockMovement = blockMovement;
            this.radius = radius;
        }

        /** 玩家是否还在允许的范围内（含维度判断） */
        public boolean inRange(ServerPlayer player) {
            if (radius <= 0) {
                return true;
            }
            if (!player.level().dimension().equals(dimension)) {
                return false;
            }
            return player.position().distanceTo(startPos) <= radius;
        }
    }

    public static void start(ServerPlayer player, String scriptId, DialogScript script) {
        Session s = new Session(scriptId, player.position(), player.level().dimension(),
                script != null && script.blocksMovement(),
                script == null ? 0 : script.radius());
        if (script != null && script.entries != null) {
            for (DialogNode node : script.entries) {
                if (node == null) {
                    continue;
                }
                s.allowed.addAll(TextUtil.stringList(node.command));
                s.allowedPlugin.addAll(TextUtil.stringList(node.pluginCommand));
                if (node.options != null) {
                    for (DialogOption opt : node.options) {
                        if (opt != null) {
                            s.allowed.addAll(TextUtil.stringList(opt.command));
                            s.allowedPlugin.addAll(TextUtil.stringList(opt.pluginCommand));
                        }
                    }
                }
            }
        }
        SESSIONS.put(player.getUUID(), s);
    }

    public static boolean isAllowed(UUID player, String scriptId, String command) {
        Session s = SESSIONS.get(player);
        if (s == null || command == null) {
            return false;
        }
        return s.scriptId.equals(scriptId) && s.allowed.contains(command);
    }

    public static boolean isPluginAllowed(UUID player, String scriptId, String command) {
        Session s = SESSIONS.get(player);
        if (s == null || command == null) {
            return false;
        }
        return s.scriptId.equals(scriptId) && s.allowedPlugin.contains(command);
    }

    /** 遍历当前所有会话（先拷一份，遍历过程中允许移除） */
    public static void forEach(BiConsumer<UUID, Session> consumer) {
        List<Map.Entry<UUID, Session>> copy = new ArrayList<>(SESSIONS.entrySet());
        for (Map.Entry<UUID, Session> e : copy) {
            consumer.accept(e.getKey(), e.getValue());
        }
    }

    public static Session get(UUID player) {
        return SESSIONS.get(player);
    }

    public static void end(UUID player) {
        SESSIONS.remove(player);
    }
}
