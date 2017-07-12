package com.lorne.tx.compensate.service;



import com.lorne.tx.compensate.model.CompensateOperationData;

import java.util.List;

/**
 * 补偿操作实现方法
 * Created by lorne on 2017/7/12.
 */
public interface CompensateOperationService {

    List<CompensateOperationData> findAll();

    void execute(CompensateOperationData data);

    String save(String className, String methodName, String groupId, String taskId, Object[] args);

    boolean delete(String id);
}
