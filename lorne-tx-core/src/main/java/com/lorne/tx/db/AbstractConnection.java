package com.lorne.tx.db;

import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.db.service.DataSourceService;
import com.lorne.tx.thread.HookRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;


/**
 * create by lorne on 2017/7/29
 */

public abstract class AbstractConnection implements Connection {


    private Logger logger = LoggerFactory.getLogger(LCNConnection.class);

    private volatile int state = 0;

    protected Connection connection;

    protected DataSourceService dataSourceService;

    private LCNDataSourceProxy.ISubNowConnection runnable;

    private int maxOutTime;

    private boolean hasGroup = false;

    private List<String> compensateList;

    private String groupId;

    protected Task waitTask;


    public AbstractConnection(Connection connection, DataSourceService dataSourceService, TxTransactionLocal transactionLocal, LCNDataSourceProxy.ISubNowConnection runnable) {
        compensateList = new ArrayList<>();
        this.connection = connection;
        this.runnable = runnable;
        this.dataSourceService = dataSourceService;
        groupId = transactionLocal.getGroupId();
        maxOutTime = transactionLocal.getMaxTimeOut();

        compensateList.add(transactionLocal.getCompensateId());

        if (!CompensateService.COMPENSATE_KEY.equals(transactionLocal.getGroupId())) {
            waitTask = ConditionUtils.getInstance().createTask(transactionLocal.getKid());
            logger.info("task-create-> " + waitTask.getKey());
        }
    }

    protected List<String> getCompensateList() {
        return compensateList;
    }

    protected void setHasIsGroup(boolean isGroup) {
        hasGroup = isGroup;
    }

    protected int getMaxOutTime() {
        return maxOutTime;
    }

    private boolean hasClose = false;

    @Override
    public void commit() throws SQLException {
        logger.info("commit");
        state = 1;

        close();
        hasClose = true;
    }

    @Override
    public void rollback() throws SQLException {
        logger.info("rollback");
        state = 0;

        close();
        hasClose = true;
    }

    protected void closeConnection() throws SQLException {
        runnable.close(this);
        connection.close();
        logger.info("close-connection->" + groupId);
    }

    @Override
    public void close() throws SQLException {
        if(hasClose){
            hasClose = false;
            return;
        }
        logger.info("close-state->" + state + "," + groupId);
        if (state == 0) {
            //再嵌套时，第一次成功后面出现回滚。
            if(waitTask!=null&&waitTask.isAwait()&&!waitTask.isRemove()) {
                //通知第一个连接回滚事务。
                waitTask.setState(0);
                waitTask.signalTask();
            }else {
                closeConnection();
                dataSourceService.deleteCompensateId(compensateList);
            }
        }
        if (state == 1) {

            if (CompensateService.COMPENSATE_KEY.equals(groupId)) {

                if(TxTransactionCompensate.current()!=null){
                    connection.commit();
                    closeConnection();
                }else {
                    //补偿事务 一概回滚
                    connection.rollback();
                    closeConnection();
                }
                logger.info("compensate - over");

            } else {
                //分布式事务

                if (hasGroup) {
                    //加入队列的连接，仅操作连接对象，不处理事务
                    return;
                }

                Runnable runnable = new HookRunnable() {
                    @Override
                    public void run0() {
                        try {
                            transaction();
                        } catch (Exception e) {
                            try {
                                connection.rollback();
                            } catch (SQLException e1) {
                                e1.printStackTrace();
                            }
                        } finally {
                            try {
                                closeConnection();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
                Thread thread = new Thread(runnable);
                thread.start();
            }

        }
    }

    protected String getGroupId() {
        return groupId;
    }

    protected Task getWaitTask() {
        return waitTask;
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(false);
    }


    protected abstract void transaction() throws SQLException;


    /*****default*******/

    @Override
    public Statement createStatement() throws SQLException {
        return connection.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return connection.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return connection.nativeSQL(sql);
    }


    @Override
    public boolean getAutoCommit() throws SQLException {
        return connection.getAutoCommit();
    }


    @Override
    public boolean isClosed() throws SQLException {
        return connection.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return connection.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        connection.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return connection.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        connection.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return connection.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        connection.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return connection.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return connection.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        connection.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return connection.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return connection.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        connection.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        connection.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return connection.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return connection.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return connection.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        connection.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        connection.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return connection.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return connection.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return connection.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return connection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return connection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return connection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return connection.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return connection.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        connection.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        connection.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return connection.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return connection.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return connection.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return connection.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        connection.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return connection.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        connection.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        connection.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return connection.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return connection.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return connection.isWrapperFor(iface);
    }
}
