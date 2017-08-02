package com.lorne.tx.service.impl;

import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Service;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service(value = "txStartCompensateTransactionServer")
public class TxStartCompensateTransactionServerImpl implements TransactionServer {



    @Override
    public Object execute(ProceedingJoinPoint point, TxTransactionInfo info) throws Throwable {

        TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
        txTransactionLocal.setHasCompensate(true);
        txTransactionLocal.setGroupId(CompensateServiceImpl.COMPENSATE_KEY);
        TxTransactionLocal.setCurrent(txTransactionLocal);

        try {
            return  point.proceed();
        }catch (Throwable e) {
            throw e;
        }finally {
            if(txTransactionLocal!=null){
                TxTransactionLocal.setCurrent(null);
            }
        }
    }
}
