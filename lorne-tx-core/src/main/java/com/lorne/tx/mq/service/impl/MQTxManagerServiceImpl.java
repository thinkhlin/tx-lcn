package com.lorne.tx.mq.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.mq.model.NotifyMsg;
import com.lorne.tx.mq.model.Request;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by lorne on 2017/6/30.
 */
@Service
public class MQTxManagerServiceImpl implements MQTxManagerService {

    @Autowired
    private NettyService nettyService;

    private Logger logger = LoggerFactory.getLogger(MQTxManagerServiceImpl.class);

    private Executor threadPool = Executors.newFixedThreadPool(100);

    @Override
    public  TxGroup createTransactionGroup() {
        JSONObject jsonObject = new JSONObject();
        Request request = new Request("cg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        return TxGroup.parser(json);
    }

    @Override
    public  TxGroup addTransactionGroup(String groupId, String taskId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        Request request = new Request("atg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        return TxGroup.parser(json);
    }


    private  void thread(String groupId, Task waitTask) {
        if (waitTask.isAwait()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("g", groupId);
            Request request = new Request("ctg", jsonObject.toString());
            String json = nettyService.sendMsg(request);
            logger.info("closeTransactionGroup->" + json);
        } else {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            thread(groupId, waitTask);
        }
    }

    @Override
    public  void closeTransactionGroup(final String groupId, final Task waitTask) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                thread(groupId, waitTask);
            }
        });
    }

    @Override
    public NotifyMsg notifyTransactionInfo(String groupId, String kid, boolean state) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("k", kid);
        jsonObject.put("s", state ? 1 : 0);
        Request request = new Request("nti", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        return NotifyMsg.parser(json);
    }


    @Override
    public  int checkTransactionInfo(String groupId, String taskId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        Request request = new Request("ckg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        int res = "1".equals(json) ? 1 : (json == null) ? -1 : 0;
        return res;
    }
}
