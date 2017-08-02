package com.lorne.tx.utils;

import com.lorne.core.framework.utils.config.ConfigUtils;

/**
 * Created by yuliang on 2017/7/15.
 */
public class ThreadPoolSizeHelper {


    //分布式事务可进入的线程最大值
    private int startSize;

    //分布式事务业务线程处理最大值
    private int inThreadSize;

    //补偿事务线程处理最大值
    private int compensateSize;

    //补偿事务业务处理最大值
    private int inCompensateSize;

    //handler消息发送最大值
    private int handlerSize;


    //消息队列处理线程
    private int mqSize;


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
        try {
            startSize = ConfigUtils.getInt("tx.properties", "max.connection.size");
        } catch (Exception e) {
            startSize = 10;
        }

        //分布式事务业务线程处理最大值
        inThreadSize = startSize;

        //补偿事务线程处理最大值
        compensateSize = 10;

        //补偿事务业务处理最大值
        inCompensateSize = 10;

        //handler消息发送最大值
        handlerSize = inThreadSize * 5;

        //消息队列处理线程
        mqSize = inThreadSize;
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


    public int getMqSize() {
        return mqSize;
    }
}
