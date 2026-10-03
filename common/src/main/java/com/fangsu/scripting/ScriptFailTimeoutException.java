package com.fangsu.scripting;

/**
 * 脚本函数处于"失败冷却期"（fail timeout）时抛出。
 * <p>
 * 与真实的脚本执行失败不同：这个异常表示本次调用<b>根本没有执行脚本</b>，
 * 只是在冷却期内被 {@code ScriptHolderBase} 拦下，属于"请稍后再试"的信号，
 * 而不是"这次又错了"。
 * <p>
 * 绘制线程（{@code GraphicsTextureHelper}）必须区分这两种情况：
 * 被冷却拦下不应计入重试次数，否则 5 次重试会在 4 秒冷却期内被瞬间耗尽
 * （每次 tick 都是一次"秒抛"），导致脚本实际上只真正执行了一次就放弃了。
 * 抛出本异常时同时携带冷却剩余时间，调用方可据此等待到冷却结束再重试。
 */
public class ScriptFailTimeoutException extends RuntimeException {

    /**
     * 距离失败冷却结束还剩多少毫秒
     */
    private final long remainingMs;

    public ScriptFailTimeoutException(String message, long remainingMs) {
        super(message);
        this.remainingMs = remainingMs;
    }

    /**
     * 距离失败冷却结束还剩多少毫秒；调用方至少应等待这么久再重试。
     */
    public long getRemainingMs() {
        return remainingMs;
    }
}
