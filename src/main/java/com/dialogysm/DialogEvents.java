package com.dialogysm;

import com.dialogysm.dialog.DialogBindings;
import com.dialogysm.dialog.DialogOpener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 交互事件：右键已绑定的 NPC → 弹出对应对话脚本。
 * （Citizens NPC 如果 Forge 侧看不到实体，就在 Citizens 里用 %player% 调用
 *  /dialogysm show <脚本> <player> 即可，效果一致。）
 */
@Mod.EventBusSubscriber(modid = DialogYSMMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogEvents {

    private DialogEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String scriptId = DialogBindings.scriptFor(event.getTarget());
        if (scriptId == null) {
            return;
        }
        // 潜行右键留给别的交互（比如骑乘/牵绳），避免全占
        if (player.isShiftKeyDown()) {
            return;
        }
        if (DialogOpener.open(player, event.getTarget(), scriptId)) {
            event.setCanceled(true);
        }
    }
}
