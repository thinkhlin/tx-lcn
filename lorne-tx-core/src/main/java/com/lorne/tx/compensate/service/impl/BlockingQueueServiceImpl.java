package com.lorne.tx.compensate.service.impl;

import com.lorne.core.framework.utils.KidUtils;
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


    private static final int max_size = 10;


    public BlockingQueueServiceImpl() {
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
            queueList.put(msg);
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

            queueList.put(msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public void init(String tableName, String unique) {

        recoverRepository.init(tableName, unique);


        for (int i = 0; i < max_size; i++) {
            Runnable runnable = new HookRunnable() {

                private void deal(QueueMsg msg) {
                    if (msg != null) {
                        if (msg.getType() == 1) {
                            recoverRepository.create(msg.getRecover());
                        } else {
                            int rs = recoverRepository.remove(msg.getId());
                            while(rs == 0) {
                                rs = recoverRepository.remove(msg.getId());
                                System.out.println("while-delete-"+msg.getId());
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
                        while ((msg = queueList.take()) != null) {
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
