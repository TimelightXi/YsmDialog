package com.dialogysm.dialog;

import com.dialogysm.DialogYSMMod;
import com.dialogysm.net.Net;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端每 10 tick 巡检一次进行中的对话：
 * - blockMovement：持续刷新缓慢效果（Slowness V），等效 Interactions 的 slow_effect
 * - endRadius：玩家跑出允许半径（或换维度）就下发关闭包并结束会话
 */
@Mod.EventBusSubscriber(modid = DialogYSMMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogSessionWatcher {

    private static int tickCounter;

    private DialogSessionWatcher() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter % 10 != 0) {
            return;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        DialogSessions.forEach((uuid, session) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                DialogSessions.end(uuid);
                return;
            }
            if (!session.inRange(player)) {
                Net.close(player, session.scriptId);
                DialogSessions.end(uuid);
                player.sendSystemMessage(Component.literal("§7你离得太远，对话已结束。"));
                DialogYSMMod.LOG.debug("[DialogYSM] 玩家 {} 超出半径，关闭对话 {}", player.getName().getString(), session.scriptId);
                return;
            }
            if (session.blockMovement) {
                // 60 tick = 3 秒，10 tick 刷一次足够续上，不会出现空档
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, true, false, false));
            }
        });
    }
}
