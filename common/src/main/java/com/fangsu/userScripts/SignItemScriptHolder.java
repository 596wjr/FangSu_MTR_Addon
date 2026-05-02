package com.fangsu.userScripts;

import org.graalvm.polyglot.Context;

public class SignItemScriptHolder extends ScriptHolderBase {

    @Override
    protected void init() {
        loadFunction("draw");
        loadFunction("getWidth");
    }
}
