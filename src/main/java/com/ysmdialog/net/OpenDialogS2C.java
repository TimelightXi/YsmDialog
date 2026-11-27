package com.ysmdialog.net;

import com.ysmdialog.client.ClientDialogs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：打开一段对话。
 * 带上说话实体 id（可选）和它的显示名 JSON，客户端只负责渲染与推进。
 */
public class OpenDialogS2C {

    public final String scriptId;
    public final String scriptJson;
    public final int speakerEntityId;
    public final String speakerNameJson;

    public OpenDialogS2C(String scriptId, String scriptJson, int speakerEntityId, String speakerNameJson) {
        this.scriptId = scriptId;
        this.scriptJson = scriptJson;
        this.speakerEntityId = speakerEntityId;
        this.speakerNameJson = speakerNameJson;
    }

    public static void encode(OpenDialogS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.scriptId, 256);
        buf.writeUtf(msg.scriptJson, 2_000_000);
        buf.writeVarInt(msg.speakerEntityId);
        buf.writeBoolean(msg.speakerNameJson != null);
        if (msg.speakerNameJson != null) {
            buf.writeUtf(msg.speakerNameJson, 4096);
        }
    }

    public static OpenDialogS2C decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(256);
        String json = buf.readUtf(2_000_000);
        int entityId = buf.readVarInt();
        String name = buf.readBoolean() ? buf.readUtf(4096) : null;
        return new OpenDialogS2C(id, json, entityId, name);
    }

    public static void handle(OpenDialogS2C msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDialogs.open(msg)));
        ctx.setPacketHandled(true);
    }
}
