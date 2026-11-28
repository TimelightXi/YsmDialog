package com.dialogysm.dialog;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * 对话选项。
 * target 可以是某个节点 id，也可以写 "end" 直接结束对话。
 */
public class DialogOption {

    /** 选项文字（字符串或文本组件） */
    public JsonElement text;

    /** 选择后跳转的节点 id；"end" 表示结束对话 */
    public String target;

    /** 选择后由服务端执行的命令（字符串或字符串数组），走原版/Forge 命令 */
    public JsonElement command;

    /** 选择后执行的插件命令（作为玩家身份从 Bukkit 侧派发，可写 %player% 占位） */
    @SerializedName("plugin_command")
    public JsonElement pluginCommand;

    /** 选完并执行完命令后，是否立刻关闭对话框（不写则按 target 走） */
    public Boolean close;

    /** 选项可见条件，格式 "execute if ..."（服务端判定后才会下发给客户端） */
    @SerializedName("visibility_command")
    public String visibilityCommand;
}
