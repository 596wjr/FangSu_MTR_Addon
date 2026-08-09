package com.fangsu.customItem;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * 模型选择项的信息接口，描述一个可选模型（如吊板 / PIDS / 屏风门的某个子模型）。
 * 仅供 {@link com.fangsu.ui.ModelSelectScreen} 等 UI 与逻辑层读取使用。
 */
public interface ModelSelectInfo {
    String getText();

    String getContent();

    String getContentText();

    @Nullable
    JsonObject getDefault();
}

