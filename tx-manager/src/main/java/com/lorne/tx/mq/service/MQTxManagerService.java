package com.lorne.tx.mq.service;

import com.lorne.tx.mq.model.TxGroup;

/**
 * Created by lorne on 2017/6/7.
 */
public interface MQTxManagerService {



    /**
     * 创建事物组
     * @param taskId
     */
    TxGroup createTransactionGroup(String taskId,String modelName);


    /**
     * 添加事务组子对象
     *
     * @return
     */
    TxGroup addTransactionGroup(String groupId, String taskId,int isGroup, String modelName);


    boolean checkTransactionGroup(String groupId,String taskId);


    boolean closeTransactionGroup(String groupId,int state);



    int getDelayTime();

}
