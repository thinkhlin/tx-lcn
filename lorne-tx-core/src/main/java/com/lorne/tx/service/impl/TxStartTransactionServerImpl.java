package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.TransactionServer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txStartTransactionServer")
public class TxStartTransactionServerImpl  implements TransactionServer {


    private Logger logger = LoggerFactory.getLogger(TxStartTransactionServerImpl.class);

    @Autowired
    protected PlatformTransactionManager txManager;

    @Autowired
    protected MQTxManagerService txManagerService;


    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {
        //分布式事务开始执行
        logger.info("tx-start");



        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = txManager.getTransaction(def);


        long t1 = System.currentTimeMillis();

        //创建事务组
        TxGroup txGroup = txManagerService.createTransactionGroup();

        //获取不到模块信息重新连接，本次事务异常返回数据.
        if (txGroup == null) {
            throw new ServiceException("创建事务组异常.");
        }

        String groupId = txGroup.getGroupId();
        TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
        txTransactionLocal.setGroupId(groupId);
        TxTransactionLocal.setCurrent(txTransactionLocal);

        Object res;
        int state = 0 ;
        try {
            res = point.proceed();
            state = 1;
        } catch ( Throwable throwable) {
            res = throwable;
            state = 0;
        }

        long t2 = System.currentTimeMillis();

        logger.info("time groupId:"+groupId+",state:"+state+" ->"+(t2-t1));
        //关闭事务组
        txManagerService.closeTransactionGroup(groupId,state);

        if(state == 1){
            txManager.commit(status);
        }else{
            txManager.rollback(status);
        }

        try {
            if(state==1){
                return res;
            }else {
                throw (Throwable)res;
            }
        }finally {
            TxTransactionLocal.setCurrent(null);
            logger.info("tx-end");
        }
    }

}
