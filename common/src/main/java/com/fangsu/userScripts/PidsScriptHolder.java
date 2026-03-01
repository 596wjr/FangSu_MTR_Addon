package com.fangsu.userScripts;

import org.graalvm.polyglot.Context;

public class PidsScriptHolder extends ScriptHolderBase {
    @Override
    protected void init(Context context) {
        loadFunction(context, "draw");
    }
}
