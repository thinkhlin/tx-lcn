package com.lorne.tx.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.service.TransactionServer;
import com.lorne.tx.service.TransactionThreadService;
import com.lorne.tx.service.model.ServiceThreadModel;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txRunningTransactionServer")
public class TxRunningTransactionServerImpl implements TransactionServer {

    private Logger logger = LoggerFactory.getLogger(TxRunningTransactionServerImpl.class);

    @Autowired
    private TransactionThreadService transactionThreadService;


    private Executor threadPool  = Executors.newFixedThreadPool(ThreadPoolSizeHelper.getInstance().getStartSize());

    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {
        //分布式事务开始执行
        logger.info("tx-running-start");

        final String txGroupId = info.getTxGroupId();
        Task groupTask =  ConditionUtils.getInstance().getTask(txGroupId);

        //当同一个事务下的业务进入切面时，合并业务执行。
        if(groupTask!=null&&!groupTask.isNotify()){
            return transactionThreadService.secondExecute(groupTask,point);
        }

        final String taskId = KidUtils.generateShortUuid();
        final Task task = ConditionUtils.getInstance().createTask(taskId);

        threadPool.execute(new Runnable() {
            @Override
            public void run() {

                TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
                txTransactionLocal.setGroupId(txGroupId);
                TxTransactionLocal.setCurrent(txTransactionLocal);


                ServiceThreadModel model = transactionThreadService.serviceInThread(info, true, txGroupId, task, point);
                if (model == null) {
                    return;
                }

                Task groupTask = ConditionUtils.getInstance().createTask(txGroupId);
                final String waitTaskKey = model.getWaitTask().getKey();
                groupTask.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        return waitTaskKey;
                    }
                });

                logger.info("taskId-id-tx-running:" + waitTaskKey);
                transactionThreadService.serviceWait(true, task, model);

                groupTask.remove();
            }
        });

        task.awaitTask();

        logger.info("tx-running-end");
        //分布式事务执行完毕
        try {
            return task.getBack().doing();
        } finally {
            task.remove();
        }
    }
}
