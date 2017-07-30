package com.lorne.tx.db;

import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.db.service.DataSourceService;
import com.lorne.tx.mq.handler.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;


/**
 * create by lorne on 2017/7/29
 */

public class LCNConnection extends AbstractConnection {

    private Logger logger = LoggerFactory.getLogger(LCNConnection.class);

    private Task waitTask;

    public LCNConnection(Connection connection, DataSourceService dataSourceService, TxTransactionLocal transactionLocal, LCNDataSourceProxy.ISubNowConnection runnable, Executor threadPool) {
        super(connection, dataSourceService, transactionLocal, runnable, threadPool);
        if(!CompensateServiceImpl.COMPENSATE_KEY.equals(transactionLocal.getGroupId())) {
            waitTask = ConditionUtils.getInstance().createTask(transactionLocal.getKid());
            logger.info("task-> "+waitTask.getKey());
        }
    }

    @Override
    public void transaction() throws SQLException {
        logger.info("transaction-> running"+transactionLocal.getGroupId());
        if(waitTask==null){
            connection.rollback();
            closeConnection();
            logger.info("waitTask is null");
            return;
        }

        //start 结束就是全部事务的结束表示,考虑start挂掉的情况
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                logger.info("自动回滚->"+transactionLocal.getGroupId());
                dataSourceService.schedule(transactionLocal.getGroupId(),waitTask);
            }
        }, 30*1000);

        waitTask.awaitTask();
        timer.cancel();

        try {
            int rs =  waitTask.getState();
            logger.info("("+transactionLocal.getGroupId()+")单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + rs);
            if(rs==1){
                connection.commit();
            }else{
                connection.rollback();
            }
        } catch (Throwable throwable) {
            connection.rollback();
        }finally {
            waitTask.remove();
        }
    }



}
