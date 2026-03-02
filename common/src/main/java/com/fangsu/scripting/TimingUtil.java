package com.fangsu.scripting;

import com.fangsu.render.scripting.AbstractScriptContext;
import net.minecraft.client.Minecraft;

@SuppressWarnings("unused")
public class TimingUtil {

    public static double runningSeconds = 0;
//    private static double timeElapsedForScript = 0;
//    private static double frameDeltaForScript = 0;
//
//    public static void prepareForScript(AbstractScriptContext scriptContext) {
//        timeElapsedForScript = runningSeconds;
//        frameDeltaForScript = timeElapsedForScript - scriptContext.lastExecuteTime;
//        scriptContext.lastExecuteTime = timeElapsedForScript;
//    }

    public static double elapsed() {
//        return timeElapsedForScript;
//        return runningSeconds;
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime() / 20.0;
        } else return 0;
    }


    public static double delta() {
//        return frameDeltaForScript;
        return 1e-1;
    }

    public static String gameTime() {
        return Long.toString(Minecraft.getInstance().level.getGameTime());
    }

    public static void onClientTick(boolean paused) {
        if (!paused) {
            runningSeconds += 1.0 / 20.0;
        }
    }
}