package com.lorne.tx.compensate.repository;

import com.alibaba.druid.pool.DruidDataSource;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    private Logger logger = LoggerFactory.getLogger(JdbcTransactionRecoverRepository.class);

    private DruidDataSource dataSource;


    @Override
    public int create(TransactionRecover recover) {
        String sql = "insert into "+tableName+"(id,retried_count,create_time,last_time,version,group_id,task_id,invocation)" +
            " values(?,?,?,?,?,?,?,?)";
        return executeUpdate(sql,recover.getId(),recover.getRetriedCount(),recover.getCreateTime(),recover.getLastTime(),recover.getVersion(),recover.getGroupId(),recover.getTaskId(),recover.getInvocation().toSerializable());
    }

    @Override
    public int remove(String id) {
        String sql = "delete from "+tableName +" where id = ? ";
        return executeUpdate(sql,id);
    }

    @Override
    public int update(String id, Date lastTime, int retriedCount) {
        String sql = "update "+tableName +" set last_time = ?, retried_count = ? where id = ? ";
        return executeUpdate(sql,lastTime,retriedCount,id);
    }

    @Override
    public List<TransactionRecover> findAll() {
        String selectSql = "select * from "+tableName +" where version = 1 ";
        List<Map<String,Object>>  list =  executeQuery(selectSql);

        String updateSql = "update "+tableName +" set version = version+1 ";
        executeUpdate(updateSql);

        List<TransactionRecover> recovers = new ArrayList<>();
        for(Map<String,Object> map:list){
            TransactionRecover recover = new TransactionRecover();

            recover.setId((String)map.get("id"));
            recover.setRetriedCount((Integer) map.get("retried_count"));
            recover.setCreateTime((Date) map.get("create_time"));
            recover.setLastTime((Date)map.get("last_time"));
            recover.setTaskId((String)map.get("task_id"));
            recover.setGroupId((String)map.get("group_id"));
            recover.setVersion((Integer) map.get("version"));
            byte[] bytes = (byte[]) map.get("invocation");
            recover.setInvocation(TransactionInvocation.parser(bytes));
            recovers.add(recover);
        }
        return recovers;
    }
    private String tableName;



    @Override
    public void init(String modelName) {
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


        this.tableName = "lcn_tx_"+modelName.replaceAll("-","_");

        String dbType = dataSource.getDbType();
        logger.info("dbType:"+dbType);
        //// TODO: 2017/7/13 扩展多中数据库的创建表语句

        String createTableSql = "CREATE TABLE `" + tableName + "` (\n" +
            "  `id` varchar(10) NOT NULL,\n" +
            "  `retried_count` int(3) NOT NULL,\n" +
            "  `create_time` datetime NOT NULL,\n" +
            "  `last_time` datetime NOT NULL,\n" +
            "  `version` int(2) NOT NULL,\n" +
            "  `group_id` varchar(10) NOT NULL,\n" +
            "  `task_id` varchar(10) NOT NULL,\n" +
            "  `invocation` longblob NOT NULL,\n" +
            "  PRIMARY KEY (`id`)\n" +
            ")";

        executeUpdate(createTableSql);
    }

    private int executeUpdate(String sql, Object... params) {
        Connection connection = null;
        PreparedStatement ps = null;
        try {
            connection = dataSource.getConnection();
            ps = connection.prepareStatement(sql);
            if(params!=null){
                for(int i=0;i<params.length;i++){
                    ps.setObject((i+1),params[i]);
                }
            }
            int rs = ps.executeUpdate();
            return rs;
        } catch (SQLException e) {
            logger.error("executeUpdate->"+e.getMessage());
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private List<Map<String,Object>> executeQuery(String sql, Object... params) {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<Map<String,Object>> list = null;
        try {
            connection = dataSource.getConnection();
            ps = connection.prepareStatement(sql);
            if(params!=null){
                for(int i=0;i<params.length;i++){
                    ps.setObject((i+1),params[i]);
                }
            }
            rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();
            list =  new ArrayList<>();
            while(rs.next()){
                Map<String,Object> rowData = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    rowData.put(md.getColumnName(i), rs.getObject(i));
                }
                list.add(rowData);
            }
        } catch (SQLException e) {
            logger.error("executeQuery->"+e.getMessage());
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return list;
    }
}
