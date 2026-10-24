package com.fangsu.render.sowcer.util;

import com.fangsu.Main;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL33;

public class GlStateTracker {

    private static int vertArrayBinding;
    private static int arrayBufBinding;
    private static int elementBufBinding;

    private static ShaderInstance currentShaderInstance;

    public static boolean isStateProtected;

    /**
     * 连续「已保护却又 capture」的次数。正常代码都用 {@code needProtection} 判断，不会走到这里；
     * 一旦有人漏了 restore，这个计数就会一直涨 —— 用它做<b>自愈</b>：
     * 超过 {@link #LEAK_STRIKE_LIMIT} 就把标志强制复位并告警一次（只读现有字段，不引入状态机）。
     */
    private static int leakStrikes;
    private static final int LEAK_STRIKE_LIMIT = 64;
    private static boolean leakWarned;
    /** restore() 未配对只告警一次，避免每帧刷屏。 */
    private static boolean restoreWarned;

    public static void capture() {
        // if (isStateProtected) throw new IllegalStateException("GlStateTracker: Nesting");
        if (isStateProtected) {
            leakStrikes++;
            if (leakStrikes < LEAK_STRIKE_LIMIT) return;
            if (!leakWarned) {
                leakWarned = true;
                // 这一条是"出事了"而不是"正常状态"：漏 restore 会让所有方速方块不再渲染，
                // 强制复位后至少下一帧还能画出来（丢掉一次错误绑定总比永久黑屏好）。
                Main.LOGGER.warn("[fangsu] GlStateTracker: {} 次嵌套 capture（疑似漏 restore），已强制复位",
                        leakStrikes);
            }
            isStateProtected = false;
            leakStrikes = 0;
        }

        vertArrayBinding = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING);
        arrayBufBinding = GL33.glGetInteger(GL33.GL_ARRAY_BUFFER_BINDING);
        elementBufBinding = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        currentShaderInstance = RenderSystem.getShader();

        isStateProtected = true;
    }

    public static void restore() {
        if (!isStateProtected) {
            // 不再抛异常：restore 现在基本都在 finally 里执行，抛异常会把原始异常盖掉、
            // 甚至让整帧绘制崩掉。这里只告警一次并直接返回（状态本来就是"未保护"，无需恢复）。
            if (!restoreWarned) {
                restoreWarned = true;
                Main.LOGGER.warn("[fangsu] GlStateTracker: restore() 时并未处于保护状态（已忽略）");
            }
            return;
        }
        GL33.glBindVertexArray(vertArrayBinding);
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, arrayBufBinding);
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, elementBufBinding);

        RenderSystem.setShader(() -> currentShaderInstance);

        // Obtain original state from RenderSystem?
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        isStateProtected = false;
        leakStrikes = 0;
    }

    public static void assertProtected() {
        if (!isStateProtected) throw new IllegalStateException("GlStateTracker: Not protected");
    }

}
