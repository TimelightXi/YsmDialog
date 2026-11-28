package com.dialogysm.dialog;

import com.google.gson.annotations.SerializedName;

/**
 * 立绘信息。
 * path 写 "npc.png" 时按 dialogysm:textures/portraits/npc.png 解析；
 * 也可以直接写完整贴图路径，如 "modid:textures/xxx/a.png"。
 */
public class PortraitSpec {

    public String path;

    /** LEFT / CENTER / RIGHT */
    public String position = "RIGHT";

    /** 亮度 0~1，用于压暗非说话方 */
    public float brightness = 1.0f;

    /** 入场动画：NONE / SLIDE_LEFT / SLIDE_RIGHT / SLIDE_UP / FADE */
    @SerializedName("animationType")
    public String animationType = "NONE";

    /** 缩放 0.2~3 */
    public float size = 1.0f;

    /** 额外整体透明度 0~1（在 brightness 之上再乘一次） */
    public float alpha = 1.0f;
}
