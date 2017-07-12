package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.JdbcTransactionRecoverRepository;
import com.lorne.tx.compensate.service.CompensateOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * mysql 版本的实现
 * Created by lorne on 2017/7/12.
 */
@Service
public class MysqlCompensateOperationServiceImpl extends CompensateOperationServiceImpl implements CompensateOperationService {


    @Autowired
    private JdbcTransactionRecoverRepository jdbcTransactionRecoverRepository;


    @Override
    public List<TransactionRecover> findAll() {
        return jdbcTransactionRecoverRepository.listAll();
    }


    @Override
    public boolean updateRetriedCount(TransactionRecover recover) {
        recover.setRetriedCount(recover.getRetriedCount()+1);
        return jdbcTransactionRecoverRepository.doUpdate(recover)>0;
    }

    @Override
    public String save(TransactionInvocation transactionInvocation,String groupId,String taskId) {
        TransactionRecover recover = new TransactionRecover();
        recover.setGroupId(groupId);
        recover.setTaskId(taskId);
        recover.setId(KidUtils.generateShortUuid());
        recover.setInvocation(transactionInvocation);
        if(jdbcTransactionRecoverRepository.doCreate(recover)>0){
            return recover.getId();
        }else{
            throw new RuntimeException("补偿数据库插入失败.");
        }
    }

    @Override
    public boolean delete(String id) {
        return jdbcTransactionRecoverRepository.doDelete(id)>0;
    }
}
