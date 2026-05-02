package com.fangsu.userScripts;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class PidsScriptHolder extends ScriptHolderBase {
    @Override
    protected void init() {
        loadFunction("draw");
    }
}
