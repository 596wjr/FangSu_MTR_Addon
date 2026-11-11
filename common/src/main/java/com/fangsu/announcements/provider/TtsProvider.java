package com.fangsu.announcements.provider;

import java.io.IOException;

/** 网络调用只由有界后台队列执行；接口不包含世界/玩家数据。 */
public interface TtsProvider {
    record Capabilities(boolean preset, boolean inlineSample, boolean registeredVoice, boolean style) { }
    /** voice 为预置/已注册 ID 或经本地校验的 data URI；不允许资源包设置 endpoint。 */
    record Request(String model, String text, String locale, String voice, String style) { }
    Capabilities capabilities();
    /** 服务默认串行；显式支持并发的提供商最多占用两路网络请求。 */
    default int maxConcurrentRequests(){return 1;}
    byte[] synthesize(Request request, String apiKey) throws IOException, InterruptedException;
}
