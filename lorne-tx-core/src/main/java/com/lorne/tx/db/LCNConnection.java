package com.lorne.tx.db;

import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.db.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;


/**
 * create by lorne on 2017/7/29
 */

public class LCNConnection extends AbstractConnection {

    private Logger logger = LoggerFactory.getLogger(LCNConnection.class);


    public LCNConnection(Connection connection, DataSourceService dataSourceService, TxTransactionLocal transactionLocal, LCNDataSourceProxy.ISubNowConnection runnable) {
        super(connection, dataSourceService, transactionLocal, runnable);
    }

    @Override
    public void transaction() throws SQLException {
        logger.info("transaction-running->" + transactionLocal.getGroupId());
        if (waitTask == null) {
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
                logger.info("自动回滚->" + transactionLocal.getGroupId());
                dataSourceService.schedule(transactionLocal.getGroupId(),transactionLocal.getCompensateId(), waitTask);
            }
        }, transactionLocal.getMaxTimeOut());

        logger.info("transaction-awaitTask->" + transactionLocal.getGroupId());

        waitTask.awaitTask();

        timer.cancel();
        try {
            int rs = waitTask.getState();
            logger.info("(" + transactionLocal.getGroupId() + ")->单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + rs);
            if (rs == 1) {
                connection.commit();
            } else {
                connection.rollback();
            }
        } catch (Throwable throwable) {
            connection.rollback();
        } finally {
            waitTask.remove();
        }
    }


}
