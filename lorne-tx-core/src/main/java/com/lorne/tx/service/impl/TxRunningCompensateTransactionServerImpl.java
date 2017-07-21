package com.lorne.tx.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.service.TransactionServer;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service(value = "txRunningCompensateTransactionServer")
public class TxRunningCompensateTransactionServerImpl implements TransactionServer {

    @Autowired
    private PlatformTransactionManager txManager;

    private Executor threadPool  = Executors.newFixedThreadPool(ThreadPoolSizeHelper.getInstance().getInCompensateSize());


    @Override
    public Object execute(final ProceedingJoinPoint point, TxTransactionInfo info) throws Throwable {
        Object obj = null;
        String kid = KidUtils.generateShortUuid();
        final Task task = ConditionUtils.getInstance().createTask(kid);
        threadPool.execute(new Runnable() {
            @Override
            public void run() {

                TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
                if(txTransactionLocal==null){
                    txTransactionLocal = new TxTransactionLocal();
                    txTransactionLocal.setHasCompensate(true);
                    txTransactionLocal.setGroupId(CompensateServiceImpl.COMPENSATE_KEY);
                    TxTransactionLocal.setCurrent(txTransactionLocal);
                }

                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus status = txManager.getTransaction(def);
                try {
                    final Object obj  =  point.proceed();
                    task.setBack(new IBack() {
                        @Override
                        public Object doing(Object... objs) throws Throwable {
                            return obj;
                        }
                    });
                }catch (final Throwable e){
                    task.setBack(new IBack() {
                        @Override
                        public Object doing(Object... objs) throws Throwable {
                            throw e;
                        }
                    });
                }
                task.signalTask();
                txManager.rollback(status);

            }
        });
        try {
            task.awaitTask();
            obj = task.getBack().doing();
            return obj;
        }finally {
            task.remove();
        }

    }
}
