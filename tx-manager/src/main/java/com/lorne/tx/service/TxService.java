package com.lorne.tx.service;

import com.lorne.tx.service.model.TxServer;
import com.lorne.tx.service.model.TxState;

/**
 * Created by lorne on 2017/7/1.
 */
public interface TxService {

    TxServer getServer();

    TxState getState();

    boolean getServerGroup(String groupId,String taskId);

    boolean getServerGroupState(String groupId);

    boolean sendMsg(String model,String msg);

}
