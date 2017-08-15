package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.db.LCNDataSourceProxy;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txRunningTransactionServer")
public class TxRunningTransactionServerImpl implements TransactionServer {


    @Autowired
    private MQTxManagerService txManagerService;


//    @Autowired
//    private CompensateService compensateService;


    private Logger logger = LoggerFactory.getLogger(TxRunningTransactionServerImpl.class);

    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {

        String kid = KidUtils.generateShortUuid();
        String txGroupId = info.getTxGroupId();
        logger.info("tx-running-start->" + txGroupId);
        long t1 = System.currentTimeMillis();

        boolean isHasIsGroup =  LCNDataSourceProxy.hasGroup(txGroupId);

       // String compensateId  = compensateService.saveTransactionInfo(info.getInvocation(), txGroupId, kid);

        TransactionRecover recover = new TransactionRecover();
        recover.setId(KidUtils.generateShortUuid());
        recover.setInvocation(info.getInvocation());
        recover.setTaskId(kid);
        recover.setGroupId(txGroupId);

        TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
        txTransactionLocal.setGroupId(txGroupId);
        txTransactionLocal.setHasStart(false);
       // txTransactionLocal.setCompensateId(compensateId);
        txTransactionLocal.setRecover(recover);
        txTransactionLocal.setKid(kid);
        txTransactionLocal.setMaxTimeOut(info.getMaxTimeOut());
        TxTransactionLocal.setCurrent(txTransactionLocal);
        try {

            Object res = point.proceed();

            Task waitTask = ConditionUtils.getInstance().getTask(kid);

            //lcn 连接已经开始等待时.
            while (waitTask!=null&&!waitTask.isAwait()) {
                TimeUnit.MILLISECONDS.sleep(1);
            }

            final TxGroup resTxGroup = txManagerService.addTransactionGroup(txGroupId, kid, isHasIsGroup);

            if (resTxGroup == null) {
                //修改事务组状态异常
                waitTask.setState(-1);
                waitTask.signalTask();
                throw new ServiceException("修改事务组状态异常." + txGroupId);
            }

            return res;
        } catch (Throwable e) {
            throw e;
        } finally {
            TxTransactionLocal.setCurrent(null);
            long t2 = System.currentTimeMillis();
            logger.info("tx-running-end->" + txGroupId+",time->"+(t2-t1));
        }
    }

}
