package com.lorne.tx.compensate.service;


import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;

import java.util.List;

/**
 * 补偿操作实现方法
 * Created by lorne on 2017/7/12.
 */
public interface BlockingQueueService {

    void setTransactionRecover(TransactionRecoverRepository recoverRepository);

    List<TransactionRecover> findAll(int state);

    void execute(TransactionRecover data);

    String save(TransactionInvocation transactionInvocation, String groupId, String taskId);

    boolean delete(String id);

    boolean updateRetriedCount(String id, int retriedCount);

    void init(String tableName,String unique);


    /**
     * 获取需要补偿的事务
     *
     * @return List<TransactionRecover>
     */
    List<TransactionRecover> loadCompensateList(int time);

}
