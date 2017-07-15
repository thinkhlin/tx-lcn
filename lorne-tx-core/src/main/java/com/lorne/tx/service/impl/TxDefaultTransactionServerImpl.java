package com.lorne.tx.service.impl;

import com.lorne.tx.bean.TransactionLocal;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txDefaultTransactionServer")
public class TxDefaultTransactionServerImpl implements TransactionServer {


    @Autowired
    private PlatformTransactionManager txManager;

    @Override
    public Object execute(ProceedingJoinPoint point, TxTransactionInfo info) throws Throwable {
        boolean hasStart = false;
        TransactionLocal transactionLocal = info.getTransactionLocal();
        if (transactionLocal == null) {
            transactionLocal = new TransactionLocal();
            TransactionLocal.setCurrent(transactionLocal);
            hasStart = true;
        }
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = txManager.getTransaction(def);

        Object obj = null;
        try {
            obj =  point.proceed();
            txManager.commit(status);
        }catch (Throwable e){
            txManager.rollback(status);
            throw e;
        }
        if (hasStart) {
            TransactionLocal.setCurrent(null);
        }
        return obj;
    }
}
