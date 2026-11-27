package com.ysmdialog.dialog;

import com.google.gson.annotations.SerializedName;

/** 对话背景大图（整屏 CG） */
public class BackgroundSpec {

    /** 同立绘的路径规则（快捷写法会按 ysmdialog:textures/backgrounds/ 解析） */
    public String path;

    /** 渲染方式：FILL（拉伸铺满）/ FIT（等比内嵌，默认） */
    @SerializedName("renderOption")
    public String renderOption = "FIT";

    /** 入场动画：NONE / FADE / SLIDE_LEFT / SLIDE_RIGHT */
    @SerializedName("animation_type")
    public String animationType = "FADE";
}
