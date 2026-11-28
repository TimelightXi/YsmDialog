package com.dialogysm.dialog;

import com.dialogysm.DialogYSMMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 对话命令派发的统一出口。
 * - command：原版 / Forge 命令，以玩家身份（权限 2）走 Forge 派发器
 * - plugin_command：Bukkit 插件命令（CMI 菜单、ChestCommands、Shopkeepers 等），
 *   优先以"玩家身份"从 Bukkit 侧派发，这样菜单类插件拿到的 sender 就是玩家本人；
 *   纯 Forge 端没有 Bukkit，自动回退到 Forge 派发器。
 * 支持 %player% / %uuid% 占位符。
 */
public final class CommandBridge {

    private CommandBridge() {
    }

    public static void runAsPlayer(ServerPlayer player, String raw) {
        String cmd = strip(raw);
        if (cmd.isEmpty()) {
            return;
        }
        try {
            player.getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(2), cmd);
        } catch (Exception ex) {
            DialogYSMMod.LOG.error("[DialogYSM] 命令执行失败: {}", cmd, ex);
        }
    }

    public static void runPlugin(ServerPlayer player, String raw) {
        String cmd = strip(raw);
        if (cmd.isEmpty()) {
            return;
        }
        cmd = cmd.replace("%player%", player.getName().getString())
                .replace("%uuid%", player.getUUID().toString());
        if (dispatchBukkit(player, cmd)) {
            return;
        }
        runAsPlayer(player, cmd);
    }

    private static String strip(String raw) {
        String s = raw == null ? "" : raw.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    /** 用反射走 Bukkit 派发（只有混合端才有 org.bukkit.*） */
    private static boolean dispatchBukkit(ServerPlayer player, String cmd) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object server = bukkit.getMethod("getServer").invoke(null);
            Class<?> senderType = Class.forName("org.bukkit.command.CommandSender");
            Object bukkitPlayer = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUUID());
            if (bukkitPlayer == null) {
                return false;
            }
            server.getClass().getMethod("dispatchCommand", senderType, String.class).invoke(server, bukkitPlayer, cmd);
            return true;
        } catch (ClassNotFoundException e) {
            // 纯 Forge 端：没有 Bukkit，交给上方回退
            return false;
        } catch (Throwable t) {
            DialogYSMMod.LOG.warn("[DialogYSM] Bukkit 派发失败，回退 Forge 执行: {}", cmd);
            return false;
        }
    }
}
