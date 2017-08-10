package com.lorne.tx.db.service;

import com.lorne.core.framework.utils.task.Task;

import java.util.List;

/**
 * create by lorne on 2017/7/29
 */
public interface DataSourceService {


    void schedule(String groupId,List<String> compensates, Task waitTask);

    void deleteCompensates(List<String> compensates);

    void deleteCompensateId(String compensateId);
}
