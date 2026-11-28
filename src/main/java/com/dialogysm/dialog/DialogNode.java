package com.dialogysm.dialog;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 一条对话（脚本里的一个节点）。
 * 字段名与 JSON 保持一致，方便直接反序列化 / 再序列化下发客户端。
 */
public class DialogNode {

    /** 节点 id，用于 next / options.target 跳转 */
    public String id;

    /** 说话者名（字符串或文本组件，留空则用绑定 NPC 的名字） */
    public JsonElement speaker;

    /** 正文（字符串 / 文本组件 / 文本组件数组） */
    public JsonElement text;

    /** 立绘列表 */
    public List<PortraitSpec> portraits;

    /** 下一条节点 id；不写则按数组顺序往下走 */
    public String next;

    /** 选项列表 */
    public List<DialogOption> options;

    /** 进入该节点时执行的命令（字符串或字符串数组），走原版/Forge 命令 */
    public JsonElement command;

    /** 进入该节点时执行的插件命令（作为玩家身份从 Bukkit 侧派发，如 cmi menu / chestcommands） */
    @SerializedName("plugin_command")
    public JsonElement pluginCommand;

    /** 该节点是否允许点击/空格跳过打字机（默认 true） */
    @SerializedName("allowSkip")
    public Boolean allowSkip;

    /** 该节点结束后直接结束整段对话 */
    @SerializedName("endDialog")
    public Boolean endDialog;

    /** 节点级可见性命令，格式为 "execute if ..."，判定失败的节点会被服务端剔除 */
    @SerializedName("visibility_command")
    public String visibilityCommand;

    /** 对话中展示的物品（如任务奖励预览） */
    @SerializedName("display_items")
    public List<ItemSpec> displayItems;

    /** 对话背景大图（可选） */
    @SerializedName("background_image")
    public BackgroundSpec backgroundImage;

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    public boolean skipAllowed() {
        return allowSkip == null || allowSkip;
    }

    public boolean endsDialog() {
        return endDialog != null && endDialog;
    }
}
