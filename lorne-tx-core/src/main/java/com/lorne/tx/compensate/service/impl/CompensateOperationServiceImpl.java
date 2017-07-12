package com.lorne.tx.compensate.service.impl;

import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.service.CompensateOperationService;
import com.lorne.tx.utils.MethodUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Created by lorne on 2017/7/12.
 */
public abstract class CompensateOperationServiceImpl implements CompensateOperationService {


    @Autowired
    private ApplicationContext applicationContext;


    @Override
    public void execute(TransactionRecover data) {
        if(data!=null){
            TransactionInvocation invocation =  data.getInvocation();
            if(invocation!=null){
                boolean isOk =  MethodUtils.invoke(applicationContext,invocation);
                if(isOk){
                    delete(data.getId());
                }else{
                    updateRetriedCount(data);
                }
            }
        }
    }

}
