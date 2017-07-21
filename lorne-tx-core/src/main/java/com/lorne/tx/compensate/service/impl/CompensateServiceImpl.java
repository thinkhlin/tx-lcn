package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.thread.CountDownLatchHelper;
import com.lorne.core.framework.utils.thread.IExecute;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.FileTransactionRecoverRepository;
import com.lorne.tx.compensate.repository.JdbcTransactionRecoverRepository;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.CompensateOperationService;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.service.ModelNameService;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class CompensateServiceImpl implements CompensateService {


    //补偿事务标示 识别groupId （远程调用时传递的参数）
    public final static String COMPENSATE_KEY = "COMPENSATE";


    public static boolean hasCompensate = false;


    @Autowired
    private CompensateOperationService compensateOperationService;

    @Autowired
    private JdbcTransactionRecoverRepository jdbcTransactionRecoverRepository;

    @Autowired
    private FileTransactionRecoverRepository fileTransactionRecoverRepository;

    @Autowired
    private ModelNameService modelNameService;

    private TransactionRecoverRepository recoverRepository;

    @Override
    public void start() {

        hasCompensate = true;

        //// TODO: 2017/7/13 获取recoverRepository对象
        recoverRepository = loadTransactionRecoverRepository();
        compensateOperationService.setTransactionRecover(recoverRepository);

        // TODO: 2017/7/11  数据库创建等操作
        compensateOperationService.init(modelNameService.getModelName());

        // TODO: 2017/7/11  查找补偿数据
        final List<TransactionRecover> list =  compensateOperationService.findAll(0);

        if(list==null||list.size()==0){
            hasCompensate = false;
            return;
        }

        for(final TransactionRecover data:list){
            compensateOperationService.execute(data);
        }
        // add Task
        new Thread(){
            @Override
            public void run() {
                while (true){
                    final List<TransactionRecover> list = compensateOperationService.findAll(2);
                    if(list==null||list.size()==0){
                        return;
                    }
                    for(TransactionRecover data:list){
                        compensateOperationService.execute(data);
                    }
                    try {
                        Thread.sleep(1000*60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();

        hasCompensate = false;
    }

    private TransactionRecoverRepository loadTransactionRecoverRepository() {
        String type = ConfigUtils.getString("tx.properties","compensate.type");
        switch (type){
            case "db":{
                return jdbcTransactionRecoverRepository;
            }
            case "file":{
                return fileTransactionRecoverRepository;
            }
        }
        return fileTransactionRecoverRepository;
    }


    @Override
    public String saveTransactionInfo(TransactionInvocation invocation, String groupId, String taskId) {
        // TODO: 2017/7/11  记录补偿数据
        return compensateOperationService.save(invocation,groupId,taskId);


    }

    @Override
    public boolean deleteTransactionInfo(String id) {
        //TODO: 2017/7/11  删除补偿数据
        return compensateOperationService.delete(id);
    }

    @Override
    public void addTask(String compensateId) {
        recoverRepository.update(compensateId,new Date(),2,1);
    }
}
