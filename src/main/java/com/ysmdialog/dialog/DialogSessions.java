package com.ysmdialog.dialog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家当前进行中的对话会话。
 * 记录本次下发的脚本里出现过的命令白名单（原版命令 / 插件命令两套），
 * 服务端收到客户端请求时校验，避免客户端伪造任意命令。
 */
public final class DialogSessions {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DialogSessions() {
    }

    public static final class Session {
        public final String scriptId;
        public final Set<String> allowed = new HashSet<>();
        public final Set<String> allowedPlugin = new HashSet<>();

        Session(String scriptId) {
            this.scriptId = scriptId;
        }
    }

    public static void start(UUID player, String scriptId, DialogScript script) {
        Session s = new Session(scriptId);
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
        SESSIONS.put(player, s);
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

    public static void end(UUID player) {
        SESSIONS.remove(player);
    }
}
