package com.lorne.tx.service;

/**
 * Created by yuliang on 2017/7/11.
 */
public interface CompensateService {

    void start();

    String  saveTransactionInfo(String className,String methodName,String groupId,String taskId,Object... args);

    boolean deleteTransactionInfo(String id);
}
