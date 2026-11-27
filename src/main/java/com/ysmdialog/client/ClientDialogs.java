package com.ysmdialog.client;

import com.ysmdialog.YsmDialogMod;
import com.ysmdialog.dialog.DialogScript;
import com.ysmdialog.dialog.TextUtil;
import com.ysmdialog.net.OpenDialogS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端侧：把服务端下发的脚本变成 DialogScreen */
public final class ClientDialogs {

    private ClientDialogs() {
    }

    public static void open(OpenDialogS2C msg) {
        DialogScript script = DialogScript.parse(msg.scriptJson);
        if (script == null) {
            YsmDialogMod.LOG.error("[YsmDialog] 客户端解析脚本失败 {}", msg.scriptId);
            return;
        }
        if (script.entries == null || script.entries.isEmpty()) {
            YsmDialogMod.LOG.warn("[YsmDialog] 脚本 {} 没有 entries", msg.scriptId);
            return;
        }
        Component speaker = msg.speakerNameJson == null ? null : TextUtil.fromJsonString(msg.speakerNameJson);
        Minecraft.getInstance().setScreen(new DialogScreen(msg.scriptId, script, speaker, msg.speakerEntityId));
    }
}
