package com.dialogysm.net;

import com.dialogysm.client.ClientDialogs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：强制关闭某段对话。
 * 用于「对话中走太远」「管理员 /dialogysm stop」这类服务端发起的关闭。
 */
public class CloseDialogS2C {

    public final String scriptId;

    public CloseDialogS2C(String scriptId) {
        this.scriptId = scriptId;
    }

    public static void encode(CloseDialogS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.scriptId == null ? "" : msg.scriptId, 256);
    }

    public static CloseDialogS2C decode(FriendlyByteBuf buf) {
        return new CloseDialogS2C(buf.readUtf(256));
    }

    public static void handle(CloseDialogS2C msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDialogs.close(msg.scriptId)));
        ctx.setPacketHandled(true);
    }
}
