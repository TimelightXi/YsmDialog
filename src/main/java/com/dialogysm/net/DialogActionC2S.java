package com.dialogysm.net;

import com.dialogysm.DialogYSMMod;
import com.dialogysm.dialog.CommandBridge;
import com.dialogysm.dialog.DialogSessions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：对话内的动作回报。
 * 出于安全考虑，服务端只执行"该脚本里确实出现过的命令"，防止客户端伪造任意命令。
 */
public class DialogActionC2S {

    public static final String ACTION_COMMAND = "command";
    public static final String ACTION_PLUGIN = "plugin";
    public static final String ACTION_END = "end";

    public final String scriptId;
    public final String action;
    public final String payload;

    public DialogActionC2S(String scriptId, String action, String payload) {
        this.scriptId = scriptId;
        this.action = action;
        this.payload = payload == null ? "" : payload;
    }

    public static void encode(DialogActionC2S msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.scriptId, 256);
        buf.writeUtf(msg.action, 64);
        buf.writeUtf(msg.payload, 4096);
    }

    public static DialogActionC2S decode(FriendlyByteBuf buf) {
        return new DialogActionC2S(buf.readUtf(256), buf.readUtf(64), buf.readUtf(4096));
    }

    public static void handle(DialogActionC2S msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            if (ACTION_END.equals(msg.action)) {
                DialogSessions.end(player.getUUID());
                DialogYSMMod.LOG.debug("[DialogYSM] 玩家 {} 结束对话 {}", player.getName().getString(), msg.scriptId);
                return;
            }
            if (ACTION_COMMAND.equals(msg.action)) {
                if (!DialogSessions.isAllowed(player.getUUID(), msg.scriptId, msg.payload)) {
                    DialogYSMMod.LOG.warn("[DialogYSM] 拒绝未授权的对话命令 player={} script={} cmd={}",
                            player.getName().getString(), msg.scriptId, msg.payload);
                    return;
                }
                CommandBridge.runAsPlayer(player, msg.payload);
            }
            if (ACTION_PLUGIN.equals(msg.action)) {
                if (!DialogSessions.isPluginAllowed(player.getUUID(), msg.scriptId, msg.payload)) {
                    DialogYSMMod.LOG.warn("[DialogYSM] 拒绝未授权的插件命令 player={} script={} cmd={}",
                            player.getName().getString(), msg.scriptId, msg.payload);
                    return;
                }
                CommandBridge.runPlugin(player, msg.payload);
            }
        });
        ctx.setPacketHandled(true);
    }
}
