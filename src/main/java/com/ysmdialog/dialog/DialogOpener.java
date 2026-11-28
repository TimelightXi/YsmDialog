package com.ysmdialog.dialog;

import com.ysmdialog.YsmDialogMod;
import com.ysmdialog.net.Net;
import com.ysmdialog.net.OpenDialogS2C;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 服务端开对话的统一入口：过滤脚本 → 记录白名单 → 下发。
 */
public final class DialogOpener {

    private DialogOpener() {
    }

    public static boolean open(ServerPlayer viewer, Entity speaker, String scriptId) {
        if (viewer == null || scriptId == null) {
            return false;
        }
        if (!DialogRegistry.has(scriptId)) {
            YsmDialogMod.LOG.warn("[YsmDialog] 脚本不存在: {}", scriptId);
            return false;
        }
        String json = DialogRegistry.jsonForPlayer(scriptId, viewer);
        if (json == null) {
            return false;
        }
        DialogScript parsed = DialogScript.parse(json);
        if (parsed == null) {
            return false;
        }
        // 冷却：同一玩家 + 同一脚本，冷却期内拒绝开启
        int cooldown = parsed.cooldownSeconds();
        long remaining = DialogCooldowns.remainingSeconds(viewer.getUUID(), scriptId);
        if (remaining > 0) {
            viewer.sendSystemMessage(Component.literal("§c这个对话还在冷却中，请 " + remaining + " 秒后再来。"));
            return false;
        }
        DialogSessions.start(viewer, scriptId, parsed);
        if (cooldown > 0) {
            DialogCooldowns.mark(viewer.getUUID(), scriptId, cooldown);
        }

        String speakerNameJson = null;
        int entityId = -1;
        if (speaker != null) {
            entityId = speaker.getId();
            try {
                speakerNameJson = Component.Serializer.toJson(speaker.getDisplayName());
            } catch (Exception ignored) {
                speakerNameJson = null;
            }
        }
        Net.open(viewer, new OpenDialogS2C(scriptId, json, entityId, speakerNameJson));
        return true;
    }
}
