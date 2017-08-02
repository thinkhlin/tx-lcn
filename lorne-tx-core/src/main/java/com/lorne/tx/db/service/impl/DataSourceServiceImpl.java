package com.lorne.tx.db.service.impl;

import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.db.service.DataSourceService;
import com.lorne.tx.mq.service.MQTxManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * create by lorne on 2017/7/29
 */
@Service
public class DataSourceServiceImpl  implements DataSourceService{

    private String url;

    private Logger logger = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    public DataSourceServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }

    @Autowired
    private MQTxManagerService txManagerService;


    private int httpCheckTransactionInfo(String groupId, String waitTaskId) {
        String json =  HttpUtils.get(url + "Group?groupId=" + groupId + "&taskId=" + waitTaskId);
        if (json == null) {
            return -1;
        }
        return json.contains("true")?1:0;
    }


    @Override
    public void schedule(String groupId, Task waitTask) {
        String waitTaskId = waitTask.getKey();
        int rs = txManagerService.checkTransactionInfo(groupId, waitTaskId);
        logger.info("schedule-checkTransactionInfo-res->"+rs);
        if(rs==1 || rs == 0){
            waitTask.setState(rs);
            waitTask.signalTask();
            logger.info("schedule-checkTransactionInfo-server->"+rs);
            return;
        }
        rs = httpCheckTransactionInfo(groupId,waitTaskId);
        logger.info("schedule-httpCheckTransactionInfo-res->"+rs);
        if(rs==1 || rs == 0){
            waitTask.setState(rs);
            waitTask.signalTask();
            logger.info("schedule-httpCheckTransactionInfo-server->"+rs);
            return;
        }
        //添加到补偿队列
        logger.info("schedule-no->"+rs);
    }
}
