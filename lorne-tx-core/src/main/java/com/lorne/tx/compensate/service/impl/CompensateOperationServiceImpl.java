package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.CompensateOperationService;
import com.lorne.tx.exception.TransactionRuntimeException;
import com.lorne.tx.utils.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

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

    /**
     * 保存数据消息队列
     */
    private BlockingQueue<TransactionRecover> saveQueue;

    /**
     * 删除数据消息队列
     */
    private BlockingQueue<String> deleteQueue;


    public CompensateOperationServiceImpl() {
        url =  ConfigUtils.getString("tx.properties","url");
        deleteQueue = new LinkedBlockingDeque<>();
        saveQueue = new LinkedBlockingDeque<>();

        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true){
                    try {
                        TransactionRecover recover = saveQueue.take();
                        if(recover!=null){
                            recoverRepository.create(recover);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }.start();


        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true){
                    try {
                        String id = deleteQueue.take();
                        if(id!=null){
                            recoverRepository.remove(id);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }.start();
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
                //通知TM
                String groupState = HttpUtils.get(url+"GroupState?groupId="+data.getGroupId()+"&taskId="+data.getTaskId());
                logger.info("获取补偿事务状态TM->"+groupState);

                if("true".equals(groupState)){
                    TxTransactionCompensate compensate = new TxTransactionCompensate();
                    TxTransactionCompensate.setCurrent(compensate);
                    boolean isOk =  MethodUtils.invoke(applicationContext,invocation);
                    if(isOk){
                        String notifyGroup = HttpUtils.get(url+"Group?groupId="+data.getGroupId()+"&taskId="+data.getTaskId());
                        logger.info("补偿事务通知TM->"+notifyGroup);
                        delete(data.getId());
                    }else{
                        updateRetriedCount(data.getId(),data.getRetriedCount()+1);
                    }
                }else{
                    //回滚操作直接清理事务补偿日志
                    delete(data.getId());
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
        try {
            saveQueue.put(recover);
            return recover.getId();
        } catch (InterruptedException e) {
            throw new TransactionRuntimeException("补偿数据库插入失败.");
        }
    }

    @Override
    public boolean updateRetriedCount(String id, int retriedCount) {
        return recoverRepository.update(id,new Date(),retriedCount)>0;
    }

    @Override
    public boolean delete(String id) {
        try {
            deleteQueue.put(id);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public void init(String modelName) {
        recoverRepository.init(modelName);
    }
}
