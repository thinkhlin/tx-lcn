package com.lorne.tx.compensate.repository;

import com.lorne.tx.compensate.model.TransactionRecover;

import java.util.Date;
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
public class JdbcTransactionRecoverRepository implements TransactionRecoverRepository {


    @Override
    public int create(TransactionRecover transactionRecover) {
        return 0;
    }

    @Override
    public int remove(String id) {
        return 0;
    }

    @Override
    public int update(String id, Date lastTime, int retriedCount) {
        return 0;
    }

    @Override
    public List<TransactionRecover> findAll() {
        return null;
    }

    @Override
    public void init() {

    }
}
