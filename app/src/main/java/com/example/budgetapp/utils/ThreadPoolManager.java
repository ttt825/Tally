package com.example.budgetapp.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池管理器
 * 统一管理应用内所有后台线程，避免直接new Thread()
 */
public class ThreadPoolManager {

    private static volatile ThreadPoolManager instance;

    // 数据库写入线程池
    private final ExecutorService databaseExecutor;

    // 通用后台任务线程池
    private final ExecutorService backgroundExecutor;

    // 定时任务线程池
    private final ScheduledExecutorService scheduledExecutor;

    // 单线程执行器（用于顺序执行的任务）
    private final ExecutorService singleExecutor;

    private ThreadPoolManager() {
        // 数据库操作：4线程
        databaseExecutor = Executors.newFixedThreadPool(4);

        // 通用后台：8线程
        backgroundExecutor = Executors.newFixedThreadPool(8);

        // 定时任务：2线程
        scheduledExecutor = Executors.newScheduledThreadPool(2);

        // 单线程执行器
        singleExecutor = Executors.newSingleThreadExecutor();
    }

    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolManager.class) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }

    // ================= 数据库操作 =================

    /**
     * 执行数据库操作
     */
    public void executeDatabase(Runnable task) {
        databaseExecutor.execute(task);
    }

    // ================= 通用后台任务 =================

    /**
     * 执行后台任务
     */
    public void executeBackground(Runnable task) {
        backgroundExecutor.execute(task);
    }

    // ================= 定时任务 =================

    /**
     * 延迟执行任务
     */
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduledExecutor.schedule(task, delay, unit);
    }

    /**
     * 定时重复执行任务
     */
    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        scheduledExecutor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    // ================= 顺序执行任务 =================

    /**
     * 顺序执行任务
     */
    public void executeSequential(Runnable task) {
        singleExecutor.execute(task);
    }

    // ================= 关闭线程池 =================

    /**
     * 关闭所有线程池
     */
    public void shutdown() {
        databaseExecutor.shutdown();
        backgroundExecutor.shutdown();
        scheduledExecutor.shutdown();
        singleExecutor.shutdown();
    }

    /**
     * 立即关闭所有线程池
     */
    public void shutdownNow() {
        databaseExecutor.shutdownNow();
        backgroundExecutor.shutdownNow();
        scheduledExecutor.shutdownNow();
        singleExecutor.shutdownNow();
    }
}
