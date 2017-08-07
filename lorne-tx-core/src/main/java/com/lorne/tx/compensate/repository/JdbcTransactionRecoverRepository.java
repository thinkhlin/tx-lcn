package com.lorne.tx.compensate.repository;

import com.alibaba.druid.pool.DruidDataSource;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.utils.SerializerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 * jdbc实现
 *
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @date 2017/7/12 10:36
 * @since JDK 1.8
 */
@Component
public class JdbcTransactionRecoverRepository implements TransactionRecoverRepository {


    @Autowired
    private SqlHelper sqlHelper;

    private Logger logger = LoggerFactory.getLogger(JdbcTransactionRecoverRepository.class);

    private DruidDataSource dataSource;

    private String dbType;

    private String tableName;

    private String unique;

    private static Connection connection = null;

    @Override
    public int create(TransactionRecover recover) {
        String sql = sqlHelper.getInsertSql(dbType,tableName);
        return executeUpdate(sql,recover.getId(),unique, recover.getRetriedCount(), recover.getGroupId(), recover.getTaskId(), SerializerUtils.serializeTransactionInvocation(recover.getInvocation()), recover.getState());
    }

    @Override
    public int remove(String id) {
        String sql = sqlHelper.getDeleteSql(dbType,tableName);
        return executeUpdate(sql, id);
    }

    @Override
    public int update(String id, int state, int retriedCount) {
        String sql = sqlHelper.getUpdateSql(dbType,tableName);
        return executeUpdate(sql, state, retriedCount, id);
    }

    @Override
    public List<TransactionRecover> findAll(int state) {
        String selectSql = sqlHelper.getFindAllByUniqueSql(dbType,tableName);
        List<Map<String, Object>> list = executeQuery(selectSql, state,unique);
       return loadList(list);
    }



    @Override
    public List<TransactionRecover> loadCompensateList(int time) {
        String selectSql = sqlHelper.loadCompensateList(dbType,tableName,time);
        List<Map<String, Object>> list = executeQuery(selectSql);
        List<TransactionRecover>  recovers =  loadList(list);
        for(TransactionRecover recover:recovers){
            update(recover.getId(),1,recover.getRetriedCount());
        }
        return recovers;
    }

    private List<TransactionRecover> loadList(List<Map<String, Object>> list){
        List<TransactionRecover> recovers = new ArrayList<>();
        for (Map<String, Object> map : list) {
            TransactionRecover recover = new TransactionRecover();
            recover.setId((String) map.get("id"));
            recover.setRetriedCount((Integer) map.get("retried_count"));
            recover.setCreateTime((Date) map.get("create_time"));
            recover.setLastTime((Date) map.get("last_time"));
            recover.setTaskId((String) map.get("task_id"));
            recover.setGroupId((String) map.get("group_id"));
            recover.setState((Integer) map.get("state"));
            recover.setUnique((String)map.get("l_unique"));
            byte[] bytes = (byte[]) map.get("invocation");
            try {
                recover.setInvocation(SerializerUtils.parserTransactionInvocation(bytes));
            } catch (Exception e) {
                e.printStackTrace();
            }
            recovers.add(recover);
        }
        return recovers;
    }

    @Override
    public void init(String tableName,String unique) {
        dataSource = new DruidDataSource();
        dataSource.setUrl(ConfigUtils.getString("tx.properties", "compensate.db.url"));
        dataSource.setUsername(ConfigUtils.getString("tx.properties", "compensate.db.username"));
        dataSource.setPassword(ConfigUtils.getString("tx.properties", "compensate.db.password"));
        dataSource.setInitialSize(2);
        dataSource.setMaxActive(20);
        dataSource.setMinIdle(0);
        dataSource.setMaxWait(60000);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestOnBorrow(false);
        dataSource.setTestWhileIdle(true);
        dataSource.setPoolPreparedStatements(false);

        dbType = ConfigUtils.getString("tx.properties", "compensate.db.dbType");

        this.tableName = tableName;
        this.unique = unique;

        // TODO: 2017/7/13 扩展多中数据库的创建表语句
        executeUpdate(sqlHelper.getCreateTableSql(dbType,tableName));
    }


    private int executeUpdate(String sql, Object... params) {
        PreparedStatement ps = null;
        try {
            if (connection == null || connection.isClosed()) {
                connection = dataSource.getConnection();
            }
            ps = connection.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject((i + 1), params[i]);
                }
            }
            int rs = ps.executeUpdate();
            return rs;
        } catch (SQLException e) {
            logger.error("executeUpdate->" + e.getMessage());
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private List<Map<String, Object>> executeQuery(String sql, Object... params) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<Map<String, Object>> list = null;
        try {
            if (connection == null || connection.isClosed()) {
                connection = dataSource.getConnection();
            }
            ps = connection.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject((i + 1), params[i]);
                }
            }
            rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();
            list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> rowData = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    rowData.put(md.getColumnName(i), rs.getObject(i));
                }
                list.add(rowData);
            }
        } catch (SQLException e) {
            logger.error("executeQuery->" + e.getMessage());
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return list;
    }
}
