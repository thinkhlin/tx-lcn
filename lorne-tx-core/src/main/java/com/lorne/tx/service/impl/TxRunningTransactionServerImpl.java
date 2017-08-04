package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txRunningTransactionServer")
public class TxRunningTransactionServerImpl implements TransactionServer {


    @Autowired
    private MQTxManagerService txManagerService;


    @Autowired
    private CompensateService compensateService;


    private Logger logger = LoggerFactory.getLogger(TxRunningTransactionServerImpl.class);

    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {

        String kid = KidUtils.generateShortUuid();
        String txGroupId = info.getTxGroupId();
        logger.info("tx-running-start->" + txGroupId);


        String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), txGroupId, kid);

        TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
        txTransactionLocal.setGroupId(txGroupId);
        txTransactionLocal.setHasStart(false);
        txTransactionLocal.setKid(kid);
        txTransactionLocal.setCompensateId(compensateId);
        TxTransactionLocal.setCurrent(txTransactionLocal);
        try {

            Object res = point.proceed();

            final TxGroup resTxGroup = txManagerService.addTransactionGroup(txGroupId, kid, TxTransactionLocal.current().isHasIsGroup());
            if (resTxGroup == null) {
                Task waitTask = ConditionUtils.getInstance().getTask(kid);
                if (waitTask != null) {
                    //修改事务组状态异常
                    waitTask.setState(-1);
                    waitTask.signalTask();
                }
                throw new ServiceException("修改事务组状态异常." + txGroupId);
            }
            return res;
        } catch (Throwable e) {
            throw e;
        } finally {
            TxTransactionLocal.setCurrent(null);
            logger.info("tx-running-end->" + txGroupId);
        }
    }

}
