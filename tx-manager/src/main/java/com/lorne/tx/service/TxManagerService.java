package com.lorne.tx.service;

import com.lorne.tx.mq.model.TxGroup;

/**
 * Created by lorne on 2017/6/7.
 */

public interface TxManagerService {


    /**
     * 创建事物组
     */
    TxGroup createTransactionGroup(String modelName);


    /**
     * 添加事务组子对象
     *
     * @return
     */
    TxGroup addTransactionGroup(String groupId, String taskId, int isGroup,String modelName);


    boolean checkTransactionGroup(String groupId,String taskId);


    boolean checkTransactionGroupState(String groupId);


    boolean closeTransactionGroup(String groupId,int state);


    void dealTxGroup(TxGroup txGroup, boolean hasOk );

    void deleteTxGroup(TxGroup txGroup);

    int getDelayTime();


    void clearNotifyData(int time);
}
