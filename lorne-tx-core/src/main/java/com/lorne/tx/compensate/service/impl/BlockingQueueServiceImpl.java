package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.compensate.model.QueueMsg;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.BlockingQueueService;
import com.lorne.tx.exception.TransactionRuntimeException;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import com.lorne.tx.utils.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class BlockingQueueServiceImpl implements BlockingQueueService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MQTxManagerService txManagerService;

    private Logger logger = LoggerFactory.getLogger(BlockingQueueServiceImpl.class);

    private TransactionRecoverRepository recoverRepository;

    private String url;


    /**
     * 保存数据消息队列
     */
    private BlockingQueue<QueueMsg> queueList;

    /**
     * 是否可以优雅关闭 程序可配置
     */
    private boolean hasGracefulClose = false;


    @Autowired
    private NettyService nettyService;


    private static final int max_size = 20;
    private final Executor threadPools = Executors.newFixedThreadPool(max_size);


    public BlockingQueueServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
        int state = 0;
        try {
            state = ConfigUtils.getInt("tx.properties", "graceful.close");
        } catch (Exception e) {
            state = 0;
        }
        if (state == 1) {
            hasGracefulClose = true;
        }
        queueList = new LinkedBlockingDeque<>();
    }

    @Override
    public void setTransactionRecover(TransactionRecoverRepository recoverRepository) {
        this.recoverRepository = recoverRepository;
    }

    @Override
    public List<TransactionRecover> findAll(int state) {
        return recoverRepository.findAll(state);
    }




    @Override
    public synchronized void execute(TransactionRecover data) {
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
                        delete(data.getId());
                        String murl = url + "Clear?groupId=" + data.getGroupId() + "&taskId=" + data.getTaskId();
                        int clearRes = txManagerService.httpClearTransactionInfo(data.getGroupId(),data.getTaskId(),false);
                        logger.info("url->"+murl+",res->"+clearRes);
                    } else {
                        updateRetriedCount(data.getId(), data.getRetriedCount() + 1);
                    }
                }else if (state==0){
                    recoverRepository.update(data.getId(), 0, 0);
                    delete(data.getId());
                }
            }
        }
    }

    @Override
    public String save(TransactionInvocation transactionInvocation, String groupId, String taskId) {
        TransactionRecover recover = new TransactionRecover();
        recover.setGroupId(groupId);
        recover.setTaskId(taskId);
        recover.setId(KidUtils.generateShortUuid());
        recover.setInvocation(transactionInvocation);
        try {
            QueueMsg msg = new QueueMsg();
            msg.setRecover(recover);
            msg.setType(1);
            if (hasGracefulClose) {
                queueList.put(msg);
            } else {
                recoverRepository.create(recover);
            }
            return recover.getId();
        } catch (Exception e) {
            throw new TransactionRuntimeException("补偿数据库插入失败.");
        }
    }

    @Override
    public boolean updateRetriedCount(String id, int retriedCount) {
        return recoverRepository.update(id,0, retriedCount) > 0;
    }

    @Override
    public boolean delete(String id) {
        try {
            QueueMsg msg = new QueueMsg();
            msg.setId(id);
            msg.setType(0);

            if (hasGracefulClose) {
                queueList.put(msg);
            } else {
                recoverRepository.remove(id);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public List<TransactionRecover> loadCompensateList(int time) {
        return recoverRepository.loadCompensateList(time);
    }

    @Override
    public void init(String tableName,String unique) {

        recoverRepository.init(tableName,unique);

        if (hasGracefulClose) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < max_size; i++) {
                        threadPools.execute(new Runnable() {
                            @Override
                            public void run() {
                                while (true) {
                                    try {
                                        QueueMsg msg = queueList.take();
                                        if (msg != null) {
                                            if (msg.getType() == 1) {
                                                recoverRepository.create(msg.getRecover());
                                            } else {
                                                int rs = recoverRepository.remove(msg.getId());
                                                if (rs == 0) {
                                                    delete(msg.getId());
                                                }
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }
                        });
                    }
                }
            };

            Thread thread = new Thread(runnable);
            thread.start();


            /**关闭时需要操作的业务**/

            Thread shutdownQueueList = new Thread(runnable);
            Runtime.getRuntime().addShutdownHook(shutdownQueueList);


            Thread shutdownNetty = new Thread(new Runnable() {
                @Override
                public void run() {
                    nettyService.close();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownNetty);


        }

    }

}
