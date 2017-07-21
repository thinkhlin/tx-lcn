package com.lorne.tx.utils;

/**
 * Created by yuliang on 2017/7/15.
 */
public class ThreadPoolSizeHelper {


    //分布式事务可进入的线程最大值
    private final int startSize=100;

    //分布式事务业务线程处理最大值
    private final int inThreadSize=startSize;

    //补偿事务线程处理最大值
    private final int compensateSize=10;

    //补偿事务业务处理最大值
    private final int inCompensateSize=10;

    //handler消息发送最大值
    private final int handlerSize=inThreadSize*4;

    private static ThreadPoolSizeHelper instance;

    public static ThreadPoolSizeHelper getInstance() {
        if (instance == null) {
            synchronized (ThreadPoolSizeHelper.class) {
                if (instance == null) {
                    instance = new ThreadPoolSizeHelper();
                }
            }
        }
        return instance;
    }

    private ThreadPoolSizeHelper() {

    }

    public int getStartSize() {
        return startSize;
    }

    public int getInThreadSize() {
        return inThreadSize;
    }

    public int getHandlerSize() {
        return handlerSize;
    }


    public int getCompensateSize() {
        return compensateSize;
    }

    public int getInCompensateSize() {
        return inCompensateSize;
    }


}
