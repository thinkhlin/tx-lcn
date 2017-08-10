package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.Constants;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.BlockingQueueService;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.service.ModelNameService;
import com.lorne.tx.utils.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class CompensateServiceImpl implements CompensateService {


    public static volatile boolean hasCompensate = true;

    private Logger logger = LoggerFactory.getLogger(CompensateServiceImpl.class);

    private String url;

    @Autowired
    private BlockingQueueService blockingQueueService;

    @Autowired
    private ModelNameService modelNameService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MQTxManagerService txManagerService;

    @Autowired
    private TransactionRecoverRepository recoverRepository;



    public CompensateServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }

    private String getTableName(String modelName) {
        Pattern pattern = Pattern.compile("[^a-z0-9A-Z]");
        Matcher matcher = pattern.matcher(modelName);
        return matcher.replaceAll("_");
    }

    private synchronized void executeService(TransactionRecover data) {
        if (data != null) {
            TransactionInvocation invocation = data.getInvocation();
            if (invocation != null) {
                //通知TM
                String stateUrl = url + "State?groupId=" + data.getGroupId() + "&taskId=" + data.getTaskId();
                int state = txManagerService.httpCheckTransactionInfo(data.getGroupId(),data.getTaskId());
                logger.info("url->"+stateUrl+",res->"+state);
                if(state==1) {
                    TxTransactionCompensate compensate = new TxTransactionCompensate();
                    TxTransactionCompensate.setCurrent(compensate);
                    boolean isOk = MethodUtils.invoke(applicationContext, invocation);
                    TxTransactionCompensate.setCurrent(null);
                    if (isOk) {
                        recoverRepository.update(data.getId(), 0, 0);
                        deleteTransactionInfo(data.getId());
                        String murl = url + "Clear?groupId=" + data.getGroupId() + "&taskId=" + data.getTaskId();
                        int clearRes = txManagerService.httpClearTransactionInfo(data.getGroupId(),data.getTaskId(),false);
                        logger.info("url->"+murl+",res->"+clearRes);
                    } else {
                        updateRetriedCount(data.getId(), data.getRetriedCount() + 1);
                    }
                }else if (state==0){
                    recoverRepository.update(data.getId(), 0, 0);
                    deleteTransactionInfo(data.getId());
                }
            }
        }
    }

    private boolean updateRetriedCount(String id, int retriedCount) {
        return recoverRepository.update(id,0, retriedCount) > 0;
    }


    @Override
    public void start() {

        hasCompensate = true;

        //// TODO: 2017/7/13 获取recoverRepository对象
        blockingQueueService.setTransactionRecover(recoverRepository);

        String tableName = "lcn_tx_"+ getTableName(modelNameService.getModelName());

        Constants.uniqueKey = modelNameService.getUniqueKey();

        // TODO: 2017/7/11  数据库创建等操作
        blockingQueueService.init(tableName,Constants.uniqueKey);

        // TODO: 2017/7/11  查找补偿数据
        List<TransactionRecover> list = recoverRepository.findAll(0);
        try {
            if (list != null && list.size() > 0) {
                for (final TransactionRecover data : list) {
                    executeService(data);
                }
            }
        } catch (Exception e) {}

        // add Task check 检查那些未能正常执行的业务数据
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {

                        try {
                            Thread.sleep(1000 * 60);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        final List<TransactionRecover> list = recoverRepository.findAll(2);
                        if (list != null && list.size() > 0) {
                            for (TransactionRecover data : list) {
                                executeService(data);
                            }
                        }
                    }catch (Exception e){}

                }
            }
        }.start();


        // add Task 检查需要补偿的数据，这里不区分是否同一个业务模块，集群下执行相同的逻辑处理。
        new Thread() {
            @Override
            public void run() {
                int maxTime = 1;//分钟
                while (true) {
                    try {
                        try {
                            Thread.sleep(1000 * 60 * maxTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        final List<TransactionRecover> list = recoverRepository.loadCompensateList(maxTime);
                        if (list != null && list.size() > 0) {
                            for (TransactionRecover data : list) {
                                executeService(data);
                            }
                        }
                    }catch (Exception e){}

                }
            }
        }.start();

        hasCompensate = false;
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

    @Override
    public int countCompensateByTaskId(String taskId) {
        return recoverRepository.countCompensateByTaskId(taskId);
    }
}
