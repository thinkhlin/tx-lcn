package com.lorne.tx.repository;

import com.lorne.tx.bean.TransactionRecover;
import com.lorne.tx.serializer.KryoSerializer;
import com.lorne.tx.serializer.ObjectSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 *  jdbc实现
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @date 2017/7/12 10:36
 * @since JDK 1.8
 */
public class JdbcTransactionRecoverRepository extends  CacheTransactionRecoverRepository {



    private JdbcTemplate jdbcTemplate;



    /**
     * 后期可以写成通过spi获取序列化方式
     */
    private ObjectSerializer serializer = new KryoSerializer();


    @Override
    protected int doCreate(TransactionRecover transactionRecover) {
        return 0;
    }

    @Override
    protected int doUpdate(TransactionRecover transactionRecover) {
        return 0;
    }

    @Override
    protected int doDelete(TransactionRecover transactionRecover) {
        return 0;
    }

    @Override
    protected TransactionRecover doFindOne(String id) {
        return null;
    }

    @Override
    protected List<TransactionRecover> doListAll() {
        return null;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
