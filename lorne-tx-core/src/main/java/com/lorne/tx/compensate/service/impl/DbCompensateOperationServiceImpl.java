package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.CompensateOperationService;
import com.lorne.tx.utils.MethodUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class DbCompensateOperationServiceImpl  implements CompensateOperationService {

    @Autowired
    private ApplicationContext applicationContext;


    @Autowired
    private TransactionRecoverRepository jdbcTransactionRecoverRepository;

    @Override
    public List<TransactionRecover> findAll() {
        return jdbcTransactionRecoverRepository.findAll();
    }


    @Override
    public void execute(TransactionRecover data) {
        if(data!=null){
            TransactionInvocation invocation =  data.getInvocation();
            if(invocation!=null){
                boolean isOk =  MethodUtils.invoke(applicationContext,invocation);
                if(isOk){
                    delete(data.getId());
                }else{
                    updateRetriedCount(data.getId(),data.getRetriedCount()+1);
                }
            }
        }
    }

    @Override
    public String save(TransactionInvocation transactionInvocation,String groupId,String taskId) {
        TransactionRecover recover = new TransactionRecover();
        recover.setGroupId(groupId);
        recover.setTaskId(taskId);
        recover.setId(KidUtils.generateShortUuid());
        recover.setInvocation(transactionInvocation);
        if(jdbcTransactionRecoverRepository.create(recover)>0){
            return recover.getId();
        }else{
            throw new RuntimeException("补偿数据库插入失败.");
        }
    }

    @Override
    public boolean updateRetriedCount(String id, int retriedCount) {
        return jdbcTransactionRecoverRepository.update(id,new Date(),retriedCount)>0;
    }

    @Override
    public boolean delete(String id) {
        return jdbcTransactionRecoverRepository.remove(id)>0;
    }

}
