package com.fangsu.userScripts;

/**
 * 列车 LCD 脚本 holder：注册脚本的 draw 函数。
 * 脚本入口：function draw(g, state, trainStatus, info, extraConfig)
 */
public class LcdScriptHolder extends ScriptHolderBase {
    @Override
    protected void init() {
        loadFunction("draw");
    }
}
