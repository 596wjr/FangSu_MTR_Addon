package com.fangsu.blockEntities;

public interface IPlatformDoor {

    boolean getDoorTarget();

    void setDoorTarget(boolean target);

    float getDoorValue();

    void setDoorValue(float value);

    /* ========= default 行为 ========= */

    /**
     * 是否开门（逻辑层）
     */
    default boolean isDoorOpen() {
        return getDoorValue() > 0;
    }

    /**
     * 给 TrainMixin 用的一步同步
     */
    default void syncFromTrain(boolean target, float value) {
        setDoorTarget(target);
        setDoorValue(value);
    }

    /**
     * 渲染用插值（如需要）
     */
    default float getDoorProgress(float partialTick) {
        return getDoorValue();
    }
}
