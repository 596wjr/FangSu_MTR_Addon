package com.fangsu.drawing.sign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个指示牌面（face）的数据：面名 + 三列内容（left/center/right）+ 背景色。
 * bgColor 为 ARGB 整数，0（完全透明）表示不填充背景，即透明。
 */
public class SignFaceData {

    private String name;
    private Map<String, List<SignItem>> lanes;
    private int bgColor; // ARGB, 默认 0 = 透明

    public SignFaceData(String name, Map<String, List<SignItem>> lanes, int bgColor) {
        this.name = name == null ? "" : name;
        this.lanes = lanes == null ? new HashMap<>() : lanes;
        this.bgColor = bgColor;
    }

    /** 兼容：未指定面名（name 为空字符串） */
    public SignFaceData(Map<String, List<SignItem>> lanes, int bgColor) {
        this("", lanes, bgColor);
    }

    /** 空的（无任何列内容）面 */
    public static SignFaceData empty() {
        Map<String, List<SignItem>> lanes = new HashMap<>();
        lanes.put("left", new ArrayList<>());
        lanes.put("center", new ArrayList<>());
        lanes.put("right", new ArrayList<>());
        return new SignFaceData("", lanes, 0);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public Map<String, List<SignItem>> getLanes() {
        return lanes;
    }

    public void setLanes(Map<String, List<SignItem>> lanes) {
        this.lanes = lanes == null ? new HashMap<>() : lanes;
    }

    public int getBgColor() {
        return bgColor;
    }

    public void setBgColor(int bgColor) {
        this.bgColor = bgColor;
    }

    /** 是否设置了不透明（alpha &gt; 0）的背景色 */
    public boolean hasBgColor() {
        return (bgColor >>> 24) != 0;
    }
}
