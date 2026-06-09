package com.fangsu.drawing.pids;

import com.fangsu.blockEntities.BlockEntityPids;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.MtrUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public class JsPidsDrawing extends BasePidsDrawing {

    private final ScriptHolderBase scriptHolder;

    public JsPidsDrawing(String scriptPath) {
        this.scriptHolder = ScriptManager.getInstance().getOrInitHolder(new ResourceLocation(scriptPath), PidsScriptHolder::new);
    }

    @Override
    public void draw(GraphicsTexture gt, List<MtrUtil.PidsArrivalInfo> arrivalInfoList,
                     Map<String, Object> drawState, int texW, int texH,
                     BlockEntityPids.DrawInfoPids drawInfo) {
        if (scriptHolder == null) return;
        // 使用同步调用确保 GraphicsTextureHelper 能正确感知绘制完成状态
        // 将 extraConfig 作为第4个参数传递给 JS 脚本（北京包脚本签名: draw(g, state, drawInfo, extraConfig)）
        ScriptManager.getInstance().requestRunFunctionSync(scriptHolder, gt::upload, "draw",
                gt.graphics, drawState, drawInfo, drawInfo.extraConfig);
    }
}
