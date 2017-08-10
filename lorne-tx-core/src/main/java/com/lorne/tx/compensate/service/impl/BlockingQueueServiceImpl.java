package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.tx.Constants;
import com.lorne.tx.compensate.model.QueueMsg;
import com.lorne.tx.compensate.model.TransactionInvocation;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.compensate.repository.TransactionRecoverRepository;
import com.lorne.tx.compensate.service.BlockingQueueService;
import com.lorne.tx.exception.TransactionRuntimeException;
import com.lorne.tx.thread.HookRunnable;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class BlockingQueueServiceImpl implements BlockingQueueService {



    private TransactionRecoverRepository recoverRepository;


    /**
     * 保存数据消息队列
     */
    private BlockingQueue<QueueMsg> queueList;

    /**
     * 是否可以优雅关闭 程序可配置
     */
    private boolean hasGracefulClose = true;



    private static final int max_size = 10;


    public BlockingQueueServiceImpl() {
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
    public void init(String tableName,String unique) {

        recoverRepository.init(tableName,unique);

        if (hasGracefulClose) {

            for (int i = 0; i < max_size; i++) {
                Runnable runnable = new HookRunnable() {

                    private void deal( QueueMsg msg ){
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
                    }

                    @Override
                    public void run0() {
                        try {
                            while (!Constants.hasExit) {
                                    QueueMsg msg = queueList.take();
                                    deal(msg);
                            }

                            QueueMsg msg;
                            while (( msg = queueList.take())!=null){
                                deal(msg);
                            }

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };

                new Thread(runnable).start();
            }

        }

    }

}
