package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txRunningTransactionServer2")
public class TxRunningTransactionServerImpl2 implements TransactionServer {


    @Autowired
    private MQTxManagerService txManagerService;


    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {

        String kid = KidUtils.generateShortUuid();


        String txGroupId = info.getTxGroupId();
        TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
        txTransactionLocal.setGroupId(txGroupId);
        txTransactionLocal.setHasStart(false);
        txTransactionLocal.setKid(kid);
        TxTransactionLocal.setCurrent(txTransactionLocal);

        try {

            Object res = point.proceed();

            System.out.println("res->"+res);

            final TxGroup resTxGroup = txManagerService.addTransactionGroup(txGroupId, kid, TxTransactionLocal.current().isHasIsGroup());

            Task waitTask = ConditionUtils.getInstance().getTask(kid);

            if (resTxGroup == null) {
                //修改事务组状态异常
                waitTask.setState(-1);
                waitTask.signalTask();

                throw new ServiceException("修改事务组状态异常."+txGroupId);
            }

            return res;
        }catch (Throwable e){
            throw e;
        }finally {
            TxTransactionLocal.setCurrent(null);
        }
    }


}
