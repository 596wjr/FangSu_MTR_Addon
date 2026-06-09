package com.fangsu.drawing.sign;

import com.fangsu.blockEntities.RouteDrawer;
import com.fangsu.drawing.ris.BaseRisDrawing;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public class JsRisDrawing extends BaseRisDrawing {

    private final ScriptHolderBase scriptHolder;

    public JsRisDrawing(String scriptPath) {
        this.scriptHolder = ScriptManager.getInstance().getOrInitHolder(new ResourceLocation(scriptPath), PidsScriptHolder::new);
    }

    @Override
    public void draw(GraphicsTexture gt, List<RouteSelectInfo> routes,
            Map<String, Object> drawState, int arrowDirection, int texW, int texH) {
        if (scriptHolder == null) {
            return;
        }
        RouteDrawer.RouteDrawInfo drawInfo = buildDrawInfo(routes, arrowDirection, texW, texH);
        // 使用同步调用确保 GraphicsTextureHelper 能正确感知绘制完成状态
        ScriptManager.getInstance().requestRunFunctionSync(scriptHolder, gt::upload, "draw", gt.graphics, drawState, drawInfo);
    }
}
