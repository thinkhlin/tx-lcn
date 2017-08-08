package com.lorne.tx.mq.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.tx.Constants;
import com.lorne.tx.mq.model.Request;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by lorne on 2017/6/30.
 */
@Service
public class MQTxManagerServiceImpl implements MQTxManagerService {

    @Autowired
    private NettyService nettyService;

    private Logger logger = LoggerFactory.getLogger(MQTxManagerServiceImpl.class);


    private String url;


    public MQTxManagerServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }

    @Override
    public TxGroup createTransactionGroup() {
        JSONObject jsonObject = new JSONObject();
        Request request = new Request("cg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        return TxGroup.parser(json);
    }

    @Override
    public TxGroup addTransactionGroup(String groupId, String taskId, boolean isGroup) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        jsonObject.put("u", Constants.uniqueKey);
        jsonObject.put("s", isGroup ? 1 : 0);
        Request request = new Request("atg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        return TxGroup.parser(json);
    }


    @Override
    public void closeTransactionGroup(final String groupId, final int state) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("s", state);
        Request request = new Request("ctg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        logger.info("closeTransactionGroup res ->" + json);
    }


    @Override
    public int checkTransactionInfo(String groupId, String taskId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", groupId);
        jsonObject.put("t", taskId);
        Request request = new Request("ckg", jsonObject.toString());
        String json = nettyService.sendMsg(request);
        int res = "1".equals(json) ? 1 : (json == null) ? -1 : 0;
        return res;
    }


    @Override
    public int httpCheckTransactionInfo(String groupId, String waitTaskId) {
        String json = HttpUtils.get(url + "Group?groupId=" + groupId + "&taskId=" + waitTaskId);
        if (json == null) {
            return -1;
        }
        return json.contains("true") ? 1 : 0;
    }


}
