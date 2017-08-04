package com.lorne.tx.compensate.service.impl;

import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.JdbcTransactionRecoverRepository;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.BlockingQueueService;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.service.ModelNameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class CompensateServiceImpl implements CompensateService {


    //补偿事务标示 识别groupId （远程调用时传递的参数）
    public final static String COMPENSATE_KEY = "COMPENSATE";


    public static volatile boolean hasCompensate = true;


    @Autowired
    private BlockingQueueService blockingQueueService;

    @Autowired
    private JdbcTransactionRecoverRepository jdbcTransactionRecoverRepository;

    @Autowired
    private ModelNameService modelNameService;

    private TransactionRecoverRepository recoverRepository;



    private String getTableName(String modelName) {
        Pattern pattern = Pattern.compile("[^a-z0-9A-Z]");
        Matcher matcher = pattern.matcher(modelName);
        return matcher.replaceAll("_");
    }

    @Override
    public void start() {

        hasCompensate = true;

        //// TODO: 2017/7/13 获取recoverRepository对象
        recoverRepository = loadTransactionRecoverRepository();
        blockingQueueService.setTransactionRecover(recoverRepository);

        String tableName = "lcn_tx_"+ getTableName(modelNameService.getModelName());

        // TODO: 2017/7/11  数据库创建等操作
        blockingQueueService.init(tableName,modelNameService.getUniqueKey());

        // TODO: 2017/7/11  查找补偿数据
        final List<TransactionRecover> list = blockingQueueService.findAll(0);

        if (list == null || list.size() == 0) {
            hasCompensate = false;
            return;
        }

        try {
            for (final TransactionRecover data : list) {
                blockingQueueService.execute(data);
            }
        } catch (Exception e) {

        }

        // add Task
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    final List<TransactionRecover> list = blockingQueueService.findAll(2);
                    if (list == null || list.size() == 0) {
                        return;
                    }
                    for (TransactionRecover data : list) {
                        blockingQueueService.execute(data);
                    }
                    try {
                        Thread.sleep(1000 * 60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();

        hasCompensate = false;
    }

    private TransactionRecoverRepository loadTransactionRecoverRepository() {
        return jdbcTransactionRecoverRepository;
    }


    @Override
    public String saveTransactionInfo(TransactionInvocation invocation, String groupId, String taskId) {
        // TODO: 2017/7/11  记录补偿数据
        return blockingQueueService.save(invocation, groupId, taskId);


    }

    @Override
    public boolean deleteTransactionInfo(String id) {
        //TODO: 2017/7/11  删除补偿数据
        return blockingQueueService.delete(id);
    }

    @Override
    public void addTask(String compensateId) {
        recoverRepository.update(compensateId, 2, 1);
    }
}
