package com.fangsu.announcements.provider;

import java.io.IOException;

/** 网络调用只由有界后台队列执行；接口不包含世界/玩家数据。 */
public interface TtsProvider {
    record Capabilities(boolean preset, boolean inlineSample, boolean registeredVoice, boolean style) { }
    /** voice 为预置/已注册 ID 或经本地校验的 data URI；不允许资源包设置 endpoint。 */
    record Request(String model, String text, String locale, String voice, String style) { }
    Capabilities capabilities();
    /** 准备远端音色。尚在审核时由服务安排重查，只有进入合成才扣本地额度。 */
    default Request prepare(Request request, String apiKey) throws IOException, InterruptedException { return request; }

    final class VoicePendingException extends IOException {
        public VoicePendingException() { super("aliyun_voice_pending"); }
    }
    /** 每次调用只合成一个片段，并发由 SynthesisService 控制。 */
    byte[] synthesize(Request request, String apiKey) throws IOException, InterruptedException;
}
