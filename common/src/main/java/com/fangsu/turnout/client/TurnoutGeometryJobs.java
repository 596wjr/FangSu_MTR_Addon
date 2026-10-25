package com.fangsu.turnout.client;

import java.util.concurrent.*;

/** 道岔几何不占用所有 OBJ 方块共用的逐帧渲染线程，队列有界，卸载时可取消。 */
public final class TurnoutGeometryJobs {
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),task->{Thread thread=new Thread(task,"fangsu-turnout-geometry");
                thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY-1);return thread;});
    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private TurnoutGeometryJobs() { }
    public static Future<?> submit(Runnable task) {
        try{return EXECUTOR.submit(task);}catch(RejectedExecutionException full){return null;}
    }
    public static void cancel(Future<?> task) {
        task.cancel(true);if(task instanceof Runnable queued)EXECUTOR.remove(queued);
    }
}
