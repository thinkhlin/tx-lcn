package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.CompensateOperationService;
import com.lorne.tx.utils.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class CompensateOperationServiceImpl implements CompensateOperationService {

    @Autowired
    private ApplicationContext applicationContext;

    private Logger logger = LoggerFactory.getLogger(CompensateOperationServiceImpl.class);

    private TransactionRecoverRepository recoverRepository;

    private String url;

    public CompensateOperationServiceImpl() {
        url =  ConfigUtils.getString("tx.properties","url");
    }

    @Override
    public void setTransactionRecover(TransactionRecoverRepository recoverRepository) {
        this.recoverRepository = recoverRepository;
    }

    @Override
    public List<TransactionRecover> findAll() {
        return recoverRepository.findAll();
    }


    @Override
    public void execute(TransactionRecover data) {
        if(data!=null){
            TransactionInvocation invocation =  data.getInvocation();
            if(invocation!=null){
                TxTransactionCompensate compensate = new TxTransactionCompensate();
                TxTransactionCompensate.setCurrent(compensate);
                boolean isOk =  MethodUtils.invoke(applicationContext,invocation);
                if(isOk){
                    //通知TM
                    String json = HttpUtils.get(url+"Group?groupId="+data.getGroupId()+"&taskId="+data.getTaskId());
                    logger.info("补偿通知tm->"+json);
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
        if(recoverRepository.create(recover)>0){
            return recover.getId();
        }else{
            throw new RuntimeException("补偿数据库插入失败.");
        }
    }

    @Override
    public boolean updateRetriedCount(String id, int retriedCount) {
        return recoverRepository.update(id,new Date(),retriedCount)>0;
    }

    @Override
    public boolean delete(String id) {
        return recoverRepository.remove(id)>0;
    }

    @Override
    public void init(String modelName) {
        recoverRepository.init(modelName);
    }
}
