package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.YesSteveModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class ThreadTools {
    /**
     * 「等待密码 / 等待连接」这类任务的总超时（毫秒）。
     * <p>
     * 这些任务以前用 {@code while (!cond) sleep(500)} 死等，非 YSM 服务器或断线时永不退出，
     * 会把线程池的线程全部占死，导致后续上传 / 下载任务永远排不到执行。
     */
    public static final long DEFAULT_WAIT_TIMEOUT_MS = 30_000L;
    /// 有界队列容量：满了直接拒绝并记日志，避免无界队列无限堆积
    private static final int QUEUE_CAPACITY = 256;
    /// 最大线程数，保持移植前的 10
    private static final int MAX_THREADS = 10;
    /// 线程序号，只用于给线程命名，方便排查线程转储
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    /**
     * 拒绝策略：只记录日志并丢弃任务，不抛异常。
     * <p>
     * 刻意不用 {@link ThreadPoolExecutor.CallerRunsPolicy}：调用方可能是 netty I/O 线程或客户端主线程，
     * 让它们同步执行模型加解密会把网络 / 渲染线程拖住，表现比丢任务更糟。
     */
    private static final RejectedExecutionHandler REJECT_HANDLER = (task, executor) ->
            YesSteveModel.LOGGER.warn("YSM: 线程池队列已满（容量 {}，活动线程 {}），丢弃任务 {}；请检查是否有阻塞任务",
                    QUEUE_CAPACITY, executor.getActiveCount(), task.getClass().getName());

    public static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2,
            MAX_THREADS,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "ysm-io-" + THREAD_SEQ.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            REJECT_HANDLER);

    private ThreadTools() {
    }

    /**
     * 轮询等待条件成立，超过 {@code timeoutMs} 立即返回 false。
     * <p>
     * 与直接 {@code while (!cond) sleep()} 的区别：总有退出路径，且被中断时恢复中断标记。
     *
     * @param condition 等待条件
     * @param timeoutMs 总超时（毫秒）
     * @return 条件在超时前成立返回 true；超时或被中断返回 false
     */
    public static boolean awaitUntil(BooleanSupplier condition, long timeoutMs) {
        // 用 nanoTime 计算截止时间，避免系统时钟被校正时提前/延后超时
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline >= 0L) {
                return false;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
