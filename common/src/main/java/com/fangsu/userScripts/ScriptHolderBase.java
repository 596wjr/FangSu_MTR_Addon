package com.fangsu.userScripts;

import org.graalvm.polyglot.*;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public abstract class ScriptHolderBase {

    protected Context context;
    protected Map<String, Value> functions = new HashMap<>();
    protected String scriptName;

    protected ScriptHolderBase() {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowCreateThread(true)
                .build();
    }

    /**
     * 加载脚本内容
     */
    public void loadScript(ResourceLocation location) throws Exception {
        scriptName = location.toString();
        Path path = Path.of(location.getPath());
        String script = Files.readString(path);

        context.eval(Source.newBuilder("js", script, location.toString()).buildLiteral());
        init(); // 子类负责注册函数
    }

    /**
     * 子类实现：注册需要的 JS 函数
     */
    protected abstract void init();

    /**
     * 注册 JS 函数到 Map
     */
    protected void loadFunction(String name) {
        Value fn = context.getBindings("js").getMember(name);
        if (fn != null && fn.canExecute()) {
            functions.put(name, fn);
        }
    }

    /**
     * 执行 JS 函数
     */
    public void runFunction(String name, Object... params) {
        Value fn = functions.get(name);
        if (fn != null) {
            fn.execute(params);
        }
    }

    public void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}