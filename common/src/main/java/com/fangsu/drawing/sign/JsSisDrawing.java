package com.fangsu.drawing.sign;

import com.fangsu.blockEntities.BlockEntitySis;
import com.fangsu.drawing.sis.BaseSisDrawing;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class JsSisDrawing extends BaseSisDrawing {

    private final ScriptHolderBase scriptHolder;

    public JsSisDrawing(String scriptPath) {
        this.scriptHolder = ScriptManager.getInstance().getOrInitHolder(new ResourceLocation(scriptPath), PidsScriptHolder::new);
    }

    @Override
    public void draw(GraphicsTexture gt, Map<String, Object> drawState,
                     int arrowDirection, int texW, int texH,
                     BlockEntitySis.SISDrawInfo drawInfo) {
        if (scriptHolder == null) return;
        // 使用同步调用确保 GraphicsTextureHelper 能正确感知绘制完成状态
        ScriptManager.getInstance().requestRunFunctionSync(scriptHolder, gt::upload, "draw", gt.graphics, drawState, drawInfo);
    }
}
