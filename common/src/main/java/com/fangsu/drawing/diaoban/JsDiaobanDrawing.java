package com.fangsu.drawing.diaoban;

import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public class JsDiaobanDrawing extends BaseDiaobanDrawing {
    private final ScriptHolderBase scriptHolder;

    public JsDiaobanDrawing(String scriptPath) {
        this.scriptHolder = ScriptManager.getInstance().getOrInitHolder(new ResourceLocation(scriptPath), PidsScriptHolder::new);
    }

    @Override
    public void draw(GraphicsTexture gt, List<RouteSelectionScreen.RouteSelectInfo> routes, Map<String, Object> drawState, int arrowDirection, int texW, int texH) {
        if (scriptHolder == null) return;
        ScriptManager.getInstance().requestRunFunctionWithCallback(scriptHolder, gt::upload, "draw", gt.graphics, drawState,
                buildDrawInfo(routes, arrowDirection, texW, texH));
    }
}
