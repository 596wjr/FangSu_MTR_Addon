package com.fangsu.modular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一份图形化程序文档 = 若干根独立脚本（每根带画布位置）。
 * 这是 ModularEditingScreen 画布编辑的顶层对象。
 */
public final class ModularDocument {

    private final List<ModularScript> scripts = new ArrayList<>();

    public List<ModularScript> getScripts() {
        return Collections.unmodifiableList(scripts);
    }

    public void addScript(ModularScript s) {
        scripts.add(s);
    }

    public void addScript(int i, ModularScript s) {
        scripts.add(Math.max(0, Math.min(i, scripts.size())), s);
    }

    public void removeScript(ModularScript s) {
        scripts.remove(s);
    }

    public ModularScript removeScript(int i) {
        return scripts.remove(i);
    }

    public void clear() {
        scripts.clear();
    }

    public boolean isEmpty() {
        return scripts.isEmpty();
    }

    public ModularDocument copy() {
        ModularDocument d = new ModularDocument();
        for (ModularScript s : scripts) d.scripts.add(s.copy());
        return d;
    }
}
