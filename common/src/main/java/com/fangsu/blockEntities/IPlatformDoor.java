package com.fangsu.blockEntities;

public interface IPlatformDoor {

    boolean getDoorTarget();

    void setDoorTarget(boolean target);

    float getDoorValue();

    void setDoorValue(float value);

    default boolean isLocked() {
        return false;
    }
}
