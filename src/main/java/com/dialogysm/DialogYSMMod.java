package com.dialogysm;

import com.mojang.logging.LogUtils;
import com.dialogysm.dialog.DialogRegistry;
import com.dialogysm.net.Net;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 模组主类：DialogYSM。
 * 只负责注册网络、命令与事件，具体的对话逻辑在 dialog / client 包里。
 */
@Mod(DialogYSMMod.MODID)
public class DialogYSMMod {

    public static final String MODID = "dialogysm";
    public static final Logger LOG = LogUtils.getLogger();

    public DialogYSMMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        // 命令、交互等 FORGE 总线事件由 DialogCommands / DialogEvents 上的 @Mod.EventBusSubscriber 自动注册
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 网络包注册必须放在 enqueueWork 里，避免类加载顺序问题
        event.enqueueWork(Net::init);
    }

    /** 服务端启动后加载脚本目录 */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            // 1.0.7 改名后首次启动：把旧的 config/ysmdialog 与存档里的 ysmdialog 数据拷过来
            LegacyMigration.run(event.getServer());
            com.dialogysm.dialog.DialogSettings.load();
            com.dialogysm.dialog.DialogTextures.extractDefaults();
            DialogRegistry.reload(event.getServer());
            com.dialogysm.dialog.DialogBindings.load(event.getServer());
            com.dialogysm.dialog.DialogCooldowns.load(event.getServer());
        }
    }
}
