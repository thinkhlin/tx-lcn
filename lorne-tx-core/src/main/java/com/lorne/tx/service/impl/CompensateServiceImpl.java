package com.lorne.tx.service.impl;

import com.lorne.tx.service.CompensateService;
import org.springframework.stereotype.Service;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class CompensateServiceImpl implements CompensateService {


    @Override
    public void start() {
        // TODO: 2017/7/11  查找补偿数据

        // TODO: 2017/7/11  执行补偿业务

    }


    @Override
    public String saveTransactionInfo(String className, String methodName, String groupId, String taskId, Object... args) {
        // TODO: 2017/7/11  记录补偿数据
        return null;
    }

    @Override
    public boolean deleteTransactionInfo(String id) {
        //TODO: 2017/7/11  删除补偿数据
        return false;
    }
}
