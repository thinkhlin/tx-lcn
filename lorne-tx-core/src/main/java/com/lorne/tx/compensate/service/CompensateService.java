package com.lorne.tx.compensate.service;

import com.lorne.tx.compensate.model.TransactionInvocation;

/**
 * Created by yuliang on 2017/7/11.
 */
public interface CompensateService {

    void start();

    String  saveTransactionInfo(TransactionInvocation invocation, String groupId, String taskId);

    boolean deleteTransactionInfo(String id);
}
