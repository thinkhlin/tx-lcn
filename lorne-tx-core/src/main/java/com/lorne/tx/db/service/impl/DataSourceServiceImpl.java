package com.lorne.tx.db.service.impl;

import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.db.service.DataSourceService;
import com.lorne.tx.mq.service.MQTxManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * create by lorne on 2017/7/29
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private String url;


    public DataSourceServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }

    @Autowired
    private MQTxManagerService txManagerService;

    @Autowired
    private CompensateService compensateService;


    private int httpCheckTransactionInfo(String groupId, String waitTaskId) {
        String json = HttpUtils.get(url + "Group?groupId=" + groupId + "&taskId=" + waitTaskId);
        if (json == null) {
            return -1;
        }
        return json.contains("true") ? 1 : 0;
    }


    @Override
    public void schedule(String groupId,String compensateId, Task waitTask) {
        String waitTaskId = waitTask.getKey();
        int rs = txManagerService.checkTransactionInfo(groupId, waitTaskId);
        if (rs == 1 || rs == 0) {
            waitTask.setState(rs);
            waitTask.signalTask();
            return;
        }
        rs = httpCheckTransactionInfo(groupId, waitTaskId);
        if (rs == 1 || rs == 0) {
            waitTask.setState(rs);
            waitTask.signalTask();
            return;
        }

        //添加到补偿队列
        waitTask.setState(-100);
        waitTask.signalTask();
        compensateService.addTask(compensateId);

    }

    @Override
    public void deleteCompensateId(String compensateId) {
        compensateService.deleteTransactionInfo(compensateId);
    }
}
