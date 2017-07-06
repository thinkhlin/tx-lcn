package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.Constants;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import com.lorne.tx.service.TransactionThreadService;
import com.lorne.tx.service.TransactionServer;
import com.lorne.tx.service.model.ExecuteAwaitTask;
import com.lorne.tx.service.model.ServiceThreadModel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private TransactionThreadService transactionThreadService;


    @Autowired
    private NettyService nettyService;


    private void confirmAwait(ExecuteAwaitTask executeAwaitTask,Task task){
        if(executeAwaitTask.getState()==1){
            task.signalTask();
        }else{
            try {
                Thread.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            confirmAwait(executeAwaitTask, task);
        }
    }


    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {
        //分布式事务开始执行
        logger.info("tx-start");


        final String taskId = KidUtils.generateShortUuid();
        final Task task = ConditionUtils.getInstance().createTask(taskId);

        final ExecuteAwaitTask executeAwaitTask = new ExecuteAwaitTask();

        Constants.threadPool.execute(new Runnable() {
            @Override
            public void run() {

                TxGroup txGroup = txManagerService.createTransactionGroup();

                //获取不到模块信息重新连接，本次事务异常返回数据.
                if (txGroup == null) {
                    task.setBack(new IBack() {
                        @Override
                        public Object doing(Object... objects) throws Throwable {
                            throw new ServiceException("添加事务组异常.");
                        }
                    });

                    confirmAwait(executeAwaitTask,task);

                    nettyService.restart();
                    return;
                }

                final String groupId = txGroup.getGroupId();

                TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
                txTransactionLocal.setGroupId(groupId);
                TxTransactionLocal.setCurrent(txTransactionLocal);


                boolean signTask = false;


                ServiceThreadModel model = transactionThreadService.serviceInThread(signTask, groupId, task, point);
                if (model == null) {
                    return;
                }
                logger.info("taskId-id-tx:" + model.getWaitTask().getKey());
                transactionThreadService.serviceWait(signTask, task, model);
            }
        });


        task.awaitTask(new IBack() {
            @Override
            public Object doing(Object... objs) throws Throwable {
                executeAwaitTask.setState(1);
                return null;
            }
        });
        logger.info("tx-end");
        //分布式事务执行完毕
        try {
            return task.getBack().doing();
        } finally {
            task.remove();
        }

    }
}
