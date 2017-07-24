package com.lorne.tx.service;

import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.service.model.ServiceThreadModel;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Created by lorne on 2017/6/9.
 */
public interface TransactionThreadService {

    ServiceThreadModel serviceInThread(TxTransactionInfo info,boolean signTask, String _groupId, Task task, ProceedingJoinPoint point);


    void serviceWait(boolean signTask, Task task, ServiceThreadModel model);

    Object secondExecute(Task groupTask , ProceedingJoinPoint point)throws Throwable;
}
