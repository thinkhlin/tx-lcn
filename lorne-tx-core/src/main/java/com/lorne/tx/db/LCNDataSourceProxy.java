package com.lorne.tx.db;

import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.db.service.DataSourceService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


/**
 * create by lorne on 2017/7/29
 */

public class LCNDataSourceProxy implements DataSource {


    protected interface ISubNowConnection {

        void close(AbstractConnection connection);

    }

    private org.slf4j.Logger logger = LoggerFactory.getLogger(LCNDataSourceProxy.class);


    private static Map<String, AbstractConnection> pools = new ConcurrentHashMap<>();


    @Autowired
    private DataSourceService dataSourceService;


    private DataSource dataSource;

    //default size
    private volatile int maxCount = 5;

    //default time (seconds)
    private int maxWaitTime = 30;

    private volatile int nowCount = 0;

    public static boolean hasGroup(String group){
        return pools.containsKey(group);
    }

    // not thread
    private ISubNowConnection subNowCount = new ISubNowConnection() {

        @Override
        public void close(AbstractConnection connection) {
            Task waitTask = connection.getWaitTask();
            if (waitTask != null) {
                if (!waitTask.isRemove()) {
                    waitTask.remove();
                }
            }

            pools.remove(connection.getGroupId());
            nowCount--;
        }
    };


    private Connection loadConnection(){
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        if(txTransactionLocal==null){
            return null;
        }
        AbstractConnection old = pools.get(txTransactionLocal.getGroupId());
        if (old != null) {
            old.setHasIsGroup(true);
            old.getCompensateList().add(txTransactionLocal.getCompensateId());

            txTransactionLocal.setHasIsGroup(true);
            TxTransactionLocal.setCurrent(txTransactionLocal);
            logger.info("get old connection ->" + txTransactionLocal.getGroupId());
            return old;
        }
        return null;
    }


    private Connection createConnection(TxTransactionLocal txTransactionLocal, Connection connection) throws SQLException {
        if (nowCount == maxCount) {
            logger.info("initLCNConnection max count ...");
            for (int i = 0; i < maxWaitTime; i++) {
                for(int j=0;j<100;j++){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (nowCount < maxCount) {
                        return createLcnConnection(connection, txTransactionLocal);
                    }
                }
            }
        } else if (nowCount < maxCount) {
            return createLcnConnection(connection, txTransactionLocal);
        } else {
            throw new SQLException("connection was overload");
        }
        return connection;
    }

    private Connection createLcnConnection(Connection connection, TxTransactionLocal txTransactionLocal) {
        nowCount++;
        LCNConnection lcn = new LCNConnection(connection, dataSourceService, txTransactionLocal, subNowCount);
        pools.put(txTransactionLocal.getGroupId(), lcn);
        logger.info("get new connection ->" + txTransactionLocal.getGroupId());
        return lcn;
    }

    private Connection initLCNConnection(Connection connection) throws SQLException {
        Connection lcnConnection = connection;
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();

        if (txTransactionLocal != null
            && StringUtils.isNotEmpty(txTransactionLocal.getGroupId())) {
            if(TxTransactionCompensate.current()!=null){
                return connection;
            }else if (CompensateServiceImpl.COMPENSATE_KEY.equals(txTransactionLocal.getGroupId())) {
                lcnConnection = createConnection(txTransactionLocal, connection);
            } else if (!txTransactionLocal.isHasStart()) {
                lcnConnection = createConnection(txTransactionLocal, connection);
            }

        }
        return lcnConnection;
    }

    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = loadConnection();
        if(connection==null) {
            return initLCNConnection(dataSource.getConnection());
        }else {
            return connection;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = loadConnection();
        if(connection==null) {
            return initLCNConnection(dataSource.getConnection(username, password));
        }else {
            return connection;
        }
    }




    /**default**/

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        dataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }
}
