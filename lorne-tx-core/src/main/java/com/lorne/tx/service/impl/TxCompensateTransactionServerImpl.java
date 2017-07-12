package com.lorne.tx.service.impl;

import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service(value = "txCompensateTransactionServer")
public class TxCompensateTransactionServerImpl implements TransactionServer {



    @Autowired
    private PlatformTransactionManager txManager;


    @Override
    public Object execute(ProceedingJoinPoint point, TxTransactionInfo info) throws Throwable {

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
        Object obj = null;
        try {
            obj =  point.proceed();
        }catch (Throwable e){
            throw e;
        }finally {
            txManager.rollback(status);
        }
        return obj;
    }
}
