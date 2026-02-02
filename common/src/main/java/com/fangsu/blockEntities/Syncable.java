package com.fangsu.blockEntities;

import net.minecraft.network.FriendlyByteBuf;

public interface Syncable {
    /**
     * 客户端：把需要同步到服务端的数据写进 buf
     */
    void writeC2S(FriendlyByteBuf buf);

    /**
     * 服务端：从 buf 读取数据并应用
     */
    void readC2S(FriendlyByteBuf buf);
}
