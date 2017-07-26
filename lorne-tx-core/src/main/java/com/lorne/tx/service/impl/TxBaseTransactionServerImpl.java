package com.lorne.tx.service.impl;

import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.model.ServiceThreadModel;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public  abstract  class TxBaseTransactionServerImpl {




    private Logger logger = LoggerFactory.getLogger(TxBaseTransactionServerImpl.class);


    @Autowired
    protected PlatformTransactionManager txManager;

    @Autowired
    protected MQTxManagerService txManagerService;

    @Autowired
    protected CompensateService compensateService;


    protected ScheduledExecutorService executorService = Executors.newScheduledThreadPool(ThreadPoolSizeHelper.getInstance().getInThreadSize());


    protected Executor threadPool  = Executors.newFixedThreadPool(ThreadPoolSizeHelper.getInstance().getStartSize());


    //以下代码必须确保原子性
    public void transactionLock(int state, TransactionStatus status, String compensateId, Task waitTask) {
        try {
            if (state == 1) {
                txManager.commit(status);
            } else {
                txManager.rollback(status);
            }
        }finally {
            compensateService.deleteTransactionInfo(compensateId);
            if (waitTask != null)
                waitTask.remove();
        }
    }

    public void transaction(Task waitTask, TransactionStatus status, ServiceThreadModel model,Task task){
        try {
            int state = (Integer) waitTask.getBack().doing();
            logger.info("单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + state);
            //事务确认操作
            transactionConfirm(state, waitTask, status, model, task);
        } catch (Throwable throwable) {
            txManager.rollback(status);
        }
    }

    abstract void transactionConfirm(int state, Task waitTask, TransactionStatus status, final ServiceThreadModel model, Task task);

}
