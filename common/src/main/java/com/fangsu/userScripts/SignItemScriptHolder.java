package com.fangsu.userScripts;

import org.graalvm.polyglot.Context;

public class SignItemScriptHolder extends ScriptHolderBase {

    @Override
    protected void init(Context context) {
        loadFunction(context, "draw");
        loadFunction(context, "getWidth");
    }
}
