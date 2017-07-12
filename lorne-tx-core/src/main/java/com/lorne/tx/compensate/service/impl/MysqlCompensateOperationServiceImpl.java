package com.lorne.tx.compensate.service.impl;

import com.lorne.tx.compensate.model.CompensateOperationData;
import com.lorne.tx.compensate.service.CompensateOperationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * mysql 版本的实现
 * Created by lorne on 2017/7/12.
 */
@Service
public class MysqlCompensateOperationServiceImpl implements CompensateOperationService {

    @Override
    public List<CompensateOperationData> findAll() {
        return null;
    }

    @Override
    public void execute(CompensateOperationData data) {

    }

    @Override
    public String save(String className, String methodName, String groupId, String taskId, Object[] args) {
        return null;
    }

    @Override
    public boolean delete(String id) {
        return false;
    }
}
