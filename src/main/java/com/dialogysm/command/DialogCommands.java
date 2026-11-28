package com.dialogysm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.dialogysm.DialogYSMMod;
import com.dialogysm.dialog.DialogBindings;
import com.dialogysm.dialog.DialogOpener;
import com.dialogysm.dialog.DialogRegistry;
import com.dialogysm.dialog.DialogScript;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/**
 * /dialogysm 命令树（别名 /ysmd）：
 *   show <脚本> [玩家]              —— 直接弹对话（无说话实体）
 *   npc <实体> <脚本> [玩家]         —— 以某个实体为说话者弹对话
 *   bind <实体> <脚本>              —— 右键该实体即可触发
 *   bind_uuid <uuid> <脚本>         —— 按 uuid 绑定（Citizens 场景）
 *   bind_tag <tag> <脚本>           —— 按 scoreboard tag 绑定
 *   unbind / unbind_uuid            —— 解除
 *   list / reload                   —— 查看脚本与绑定表、重载
 * 权限等级 2（OP）。
 */
@Mod.EventBusSubscriber(modid = DialogYSMMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogCommands {

    private DialogCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dialogysm")
                .requires(source -> source.hasPermission(2));

        // ---- show ----
        root.then(Commands.literal("show")
                .then(Commands.argument("script", StringArgumentType.word())
                        .executes(ctx -> showToSelf(ctx, StringArgumentType.getString(ctx, "script")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showTo(ctx, null,
                                        StringArgumentType.getString(ctx, "script"),
                                        EntityArgument.getPlayer(ctx, "player"))
                                )
                        )
                )
        );

        // ---- npc ----
        root.then(Commands.literal("npc")
                .then(Commands.argument("npc", EntityArgument.entity())
                        .then(Commands.argument("script", StringArgumentType.word())
                                .executes(ctx -> showTo(ctx, EntityArgument.getEntity(ctx, "npc"),
                                        StringArgumentType.getString(ctx, "script"),
                                        ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> showTo(ctx, EntityArgument.getEntity(ctx, "npc"),
                                                StringArgumentType.getString(ctx, "script"),
                                                EntityArgument.getPlayer(ctx, "player"))
                                        )
                                )
                        )
                )
        );

        // ---- bind ----
        root.then(Commands.literal("bind")
                .then(Commands.argument("npc", EntityArgument.entity())
                        .then(Commands.argument("script", StringArgumentType.word())
                                .executes(DialogCommands::bindEntity))));

        root.then(Commands.literal("bind_uuid")
                .then(Commands.argument("uuid", StringArgumentType.word())
                        .then(Commands.argument("script", StringArgumentType.word())
                                .executes(DialogCommands::bindUuid))));

        root.then(Commands.literal("bind_tag")
                .then(Commands.argument("tag", StringArgumentType.word())
                        .then(Commands.argument("script", StringArgumentType.word())
                                .executes(DialogCommands::bindTag))));

        // ---- unbind ----
        root.then(Commands.literal("unbind")
                .then(Commands.argument("npc", EntityArgument.entity())
                        .executes(DialogCommands::unbindEntity)));

        root.then(Commands.literal("unbind_uuid")
                .then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(DialogCommands::unbindUuid)));

        // ---- 管理 ----
        root.then(Commands.literal("list").executes(DialogCommands::list));
        root.then(Commands.literal("reload").executes(DialogCommands::reload));

        LiteralCommandNode<CommandSourceStack> main = event.getDispatcher().register(root);
        // 短别名 /ysmd
        event.getDispatcher().register(Commands.literal("ysmd")
                .requires(source -> source.hasPermission(2))
                .redirect(main));
    }

    private static int showToSelf(CommandContext<CommandSourceStack> ctx, String scriptId) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (self == null) {
            ctx.getSource().sendFailure(Component.literal("控制台执行请写成：/dialogysm show <脚本> <玩家>"));
            return 0;
        }
        return showTo(ctx, null, scriptId, self);
    }

    private static int showTo(CommandContext<CommandSourceStack> ctx, Entity speaker, String scriptId, ServerPlayer viewer) {
        if (!DialogRegistry.has(scriptId)) {
            return fail(ctx, scriptId);
        }
        boolean ok = DialogOpener.open(viewer, speaker, scriptId);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "已向 " + viewer.getName().getString() + " 打开对话脚本 " + scriptId), false);
            return 1;
        }
        return 0;
    }

    private static int bindEntity(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "npc");
        String scriptId = StringArgumentType.getString(ctx, "script");
        if (!DialogRegistry.has(scriptId)) {
            return fail(ctx, scriptId);
        }
        DialogBindings.bindEntity(entity.getUUID(), scriptId, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("已将 " + entity.getDisplayName().getString()
                + "（" + entity.getUUID() + "）绑定到脚本 " + scriptId
                + "，右键该 NPC 即可触发"), true);
        return 1;
    }

    private static int bindUuid(CommandContext<CommandSourceStack> ctx) {
        String rawUuid = StringArgumentType.getString(ctx, "uuid");
        String scriptId = StringArgumentType.getString(ctx, "script");
        if (!DialogRegistry.has(scriptId)) {
            return fail(ctx, scriptId);
        }
        try {
            UUID uuid = UUID.fromString(rawUuid);
            DialogBindings.bindEntity(uuid, scriptId, ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("已绑定 uuid " + uuid + " → " + scriptId), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("uuid 格式不对: " + rawUuid));
            return 0;
        }
    }

    private static int bindTag(CommandContext<CommandSourceStack> ctx) {
        String tag = StringArgumentType.getString(ctx, "tag");
        String scriptId = StringArgumentType.getString(ctx, "script");
        if (!DialogRegistry.has(scriptId)) {
            return fail(ctx, scriptId);
        }
        DialogBindings.bindTag(tag, scriptId, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("已把 tag「" + tag + "」绑定到脚本 " + scriptId), true);
        return 1;
    }

    private static int unbindEntity(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(ctx, "npc");
        DialogBindings.unbindEntity(entity.getUUID(), ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("已解除绑定 " + entity.getUUID()), true);
        return 1;
    }

    private static int unbindUuid(CommandContext<CommandSourceStack> ctx) {
        try {
            UUID uuid = UUID.fromString(StringArgumentType.getString(ctx, "uuid"));
            DialogBindings.unbindEntity(uuid, ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("已解除绑定 " + uuid), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("uuid 格式不对"));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("可用脚本:"), false);
        for (String id : DialogRegistry.ids()) {
            String title = id;
            try {
                DialogScript s = DialogScript.parse(DialogRegistry.rawJson(id));
                if (s != null && s.title != null) {
                    title = s.title;
                }
            } catch (Exception ignored) {
            }
            final String line = " - " + id + "  «" + title + "»";
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        Map<String, String> all = DialogBindings.snapshot();
        if (!all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("当前绑定:"), false);
            all.forEach((k, v) -> {
                final String line = " - " + k + " → " + v;
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            });
        }
        java.util.List<String> portraits = com.dialogysm.dialog.DialogTextures.listPortraits();
        final String portraitLine = "config 立绘(" + portraits.size() + "): "
                + (portraits.isEmpty() ? "无" : String.join(", ", portraits));
        ctx.getSource().sendSuccess(() -> Component.literal(portraitLine), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        com.dialogysm.dialog.DialogSettings.load();
        com.dialogysm.dialog.DialogTextures.extractDefaults();
        DialogRegistry.reload(ctx.getSource().getServer());
        DialogBindings.load(ctx.getSource().getServer());
        com.dialogysm.dialog.DialogCooldowns.load(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("已重载 " + DialogRegistry.ids().size()
                + " 个脚本、绑定表、冷却表与设置；可用立绘 "
                + com.dialogysm.dialog.DialogTextures.listPortraits().size() + " 张"), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String scriptId) {
        ctx.getSource().sendFailure(Component.literal("找不到对话脚本: " + scriptId + "（可用 /dialogysm list 查看）"));
        return 0;
    }
}
