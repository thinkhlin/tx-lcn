package com.lorne.tx.utils;

import com.lorne.core.framework.utils.config.ConfigUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by yuliang on 2017/7/15.
 */
public class ThreadPoolUtils {

    private Executor threadPool = null;
    private ScheduledExecutorService executorService = null;

    private static ThreadPoolUtils instance;

    public static ThreadPoolUtils getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolUtils.class) {
                if (instance == null) {
                    instance = new ThreadPoolUtils();
                }
            }
        }
        return instance;
    }

    private ThreadPoolUtils() {
        try {
            int size = ConfigUtils.getInt("tx.properties", "max.thread.size");
            threadPool = Executors.newFixedThreadPool(size);
        } catch (Exception e) {
            threadPool = Executors.newFixedThreadPool(10);
        }
        executorService = Executors.newScheduledThreadPool(50);
    }

    public void execute(Runnable runnable) {
        threadPool.execute(runnable);
    }

    public void schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        executorService.schedule(runnable, delay, timeUnit);
    }

}
