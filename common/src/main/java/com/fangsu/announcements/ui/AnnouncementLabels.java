package com.fangsu.announcements.ui;

import com.fangsu.mappings.ComponentHelper;

/** 内置项目显示用途名称，资源包自定义 ID 原样保留便于作者识别。 */
public final class AnnouncementLabels {
    public static String template(String id){
        String key=switch(id){case "fangsu:platform_arrival_a"->"platform_a";case "fangsu:platform_arrival_b"->"platform_b";case "fangsu:train_next"->"train_next";case "fangsu:train_approaching"->"train_approaching";case "fangsu:train_arrived"->"train_arrived";default->"";};
        return key.isEmpty()?id:ComponentHelper.translatableString("ui.fangsu.menu.label_"+key);
    }
    public static String voice(String id){
        if(id.equals("fangsu:imported"))return ComponentHelper.translatableString("ui.fangsu.ann.library_imported");
        String key=switch(id){case "fangsu:bjmtr_zh"->"bjmtr_zh";case "fangsu:bjmtr_en"->"bjmtr_en";default->"";};
        return key.isEmpty()?id:ComponentHelper.translatableString("ui.fangsu.menu.label_"+key);
    }
    public static String variant(String id){return id.equals("standard")?ComponentHelper.translatableString("ui.fangsu.menu.variant_standard"):id;}
    public static String locale(String id){return id.equals("zh-CN")?ComponentHelper.translatableString("ui.fangsu.menu.label_zh_cn"):id.equals("en-US")?ComponentHelper.translatableString("ui.fangsu.menu.label_en_us"):id;}
}
