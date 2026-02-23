package com.fangsu.render.scripting;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

public abstract class AbstractScriptContext {
    //    public Scriptable state;
    protected boolean created = false;
    public Future<?> scriptStatus;
    public double lastExecuteTime = 0;

    protected boolean disposed = false;

    public long lastExecuteDuration = 0;
    public Map<String, String> debugInfo = new HashMap<>();

    public abstract void renderFunctionFinished();

    public abstract Object getWrapperObject();

//    public abstract String getContextTypeName();

    public abstract boolean isBearerAlive();

    public void setDebugInfo(String key, String value) {
        debugInfo.put(key, value);
    }
}
