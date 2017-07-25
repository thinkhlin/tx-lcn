package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.mq.handler.TransactionHandler;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import com.lorne.tx.service.TransactionServer;
import com.lorne.tx.service.model.ServiceThreadModel;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.*;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txStartTransactionServer")
public class TxStartTransactionServerImpl implements TransactionServer {


    private Logger logger = LoggerFactory.getLogger(TxStartTransactionServerImpl.class);

    @Autowired
    private MQTxManagerService txManagerService;


    @Autowired
    private NettyService nettyService;

    @Autowired
    private PlatformTransactionManager txManager;


    @Autowired
    private CompensateService compensateService;


    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(ThreadPoolSizeHelper.getInstance().getInThreadSize());
    private Executor threadPool  = Executors.newFixedThreadPool(ThreadPoolSizeHelper.getInstance().getStartSize());


    public ServiceThreadModel serviceInThread(String waitTaskKey,TxTransactionInfo info, String groupId,TxGroup txGroup,ProceedingJoinPoint point) {

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = txManager.getTransaction(def);
        Task waitTask = ConditionUtils.getInstance().createTask(waitTaskKey);
        ServiceThreadModel model = new ServiceThreadModel();
        Object res;
        try {
            res = point.proceed();
            String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), groupId, waitTaskKey);
            model.setCompensateId(compensateId);
        } catch ( Throwable throwable) {
            res = throwable;
        }

        model.setStatus(status);
        model.setWaitTask(waitTask);
        model.setTxGroup(txGroup);
        model.setObj(res);
        return model;
    }

    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {
        //分布式事务开始执行
        logger.info("tx-start");


        final String taskId = KidUtils.generateShortUuid();
        final Task task = ConditionUtils.getInstance().createTask(taskId);


        threadPool.execute(new Runnable() {
            @Override
            public void run() {

                final String waitTaskKey = KidUtils.generateShortUuid();

                //创建事务组
                TxGroup txGroup = txManagerService.createTransactionGroup(waitTaskKey);

                //获取不到模块信息重新连接，本次事务异常返回数据.
                if (txGroup == null) {
                    task.setBack(new IBack() {
                        @Override
                        public Object doing(Object... objects) throws Throwable {
                            throw new ServiceException("创建事务组异常.");
                        }
                    });
                    task.signalTask();
                    if(!TransactionHandler.net_state) {
                        nettyService.restart();
                    }
                    return;
                }

                final String groupId = txGroup.getGroupId();

                TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
                txTransactionLocal.setGroupId(groupId);
                TxTransactionLocal.setCurrent(txTransactionLocal);



                ServiceThreadModel model = serviceInThread(waitTaskKey,info,groupId,txGroup,point);


                Task groupTask = ConditionUtils.getInstance().createTask(txGroup.getGroupId());

                groupTask.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        return waitTaskKey;
                    }
                });

                logger.info("taskId-id-tx:" + waitTaskKey);
                serviceWait(task, model);

                groupTask.remove();
            }
        });


        task.awaitTask();
        logger.info("tx-end");
        //分布式事务执行完毕
        try {
            return task.getBack().doing();
        } finally {
            task.remove();
        }

    }




    public void serviceWait(final Task task, final ServiceThreadModel model) {
        final Task waitTask = model.getWaitTask();
       // final String taskId = waitTask.getKey();
        TransactionStatus status = model.getStatus();

        long st = model.getTxGroup().getStartTime();
        long et =model.getTxGroup().getNowTime();

        int tmTime = model.getTxGroup().getWaitTime();

        long time = tmTime*1000 - ((int) (et - st));


        //等待线程
        ScheduledFuture future = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                waitTask.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        return -2;
                    }
                });
                waitTask.signalTask();
            }
        },time,TimeUnit.MILLISECONDS);


        //关闭事务组
        txManagerService.closeTransactionGroup(model.getTxGroup().getGroupId(),model.getObj() instanceof Throwable ?0:1, waitTask);


        logger.info("进入回滚等待.");
        waitTask.awaitTask();



        //关闭自动回滚业务
        if(!future.isDone()){
            future.cancel(false);
        }

        try {
            int state = (Integer) waitTask.getBack().doing();
            logger.info("单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + state);
            //事务确认操作
            transactionConfirm(state, waitTask, status, model, task);
        } catch (Throwable throwable) {
            txManager.rollback(status);
        }
    }



    //事务确认状态
    private void transactionConfirm(int state, Task waitTask, TransactionStatus status, final ServiceThreadModel model, Task task) {
        transactionLock(state, status, model.getCompensateId(), waitTask);

        task.setBack(new IBack() {
            @Override
            public Object doing(Object... objs) throws Throwable {
                if(model.getObj() instanceof  Throwable){
                    throw (Throwable) model.getObj();
                }
                return model.getObj();
            }
        });

        if (state == -2) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objs) throws Throwable {
                    throw new Throwable("事务模块超时异常.");
                }
            });
        }

        //主程序的业务数据返回
        task.signalTask();

    }

    //以下代码必须确保原子性
    private void transactionLock(int state, TransactionStatus status, String compensateId, Task waitTask) {
        try {
            if (state == 1) {
                txManager.commit(status);
            } else {
                txManager.rollback(status);
            }
        }finally {
            if(StringUtils.isNotEmpty((compensateId))){
                compensateService.deleteTransactionInfo(compensateId);
            }
            if (waitTask != null)
                waitTask.remove();
        }
    }

}
