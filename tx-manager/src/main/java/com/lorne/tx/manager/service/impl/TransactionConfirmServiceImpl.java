package com.lorne.tx.manager.service.impl;


import com.alibaba.fastjson.JSONObject;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.core.framework.utils.thread.CountDownLatchHelper;
import com.lorne.core.framework.utils.thread.IExecute;
import com.lorne.tx.manager.model.ExecuteAwaitTask;
import com.lorne.tx.manager.service.TransactionConfirmService;
import com.lorne.tx.manager.service.TxManagerService;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.model.TxInfo;
import com.lorne.tx.socket.SocketManager;
import com.lorne.tx.socket.utils.SocketUtils;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by lorne on 2017/6/9.
 */
@Service
public class TransactionConfirmServiceImpl implements TransactionConfirmService {


    private Logger logger = LoggerFactory.getLogger(TransactionConfirmServiceImpl.class);

    private ScheduledExecutorService executorService  = Executors.newScheduledThreadPool(50);

    private Executor threadPool = Executors.newFixedThreadPool(50);

    @Autowired
    private TxManagerService txManagerService;

    @Override
    public void confirm(TxGroup txGroup) {
        logger.info("end:" + txGroup.toJsonString());
        boolean checkState = true;


        //检查事务是否正常
        for (TxInfo info : txGroup.getList()) {
            if (info.getState() == 0) {
                checkState = false;
            }
        }



        //绑定管道对象，检查网络
        boolean isOk = reloadChannel(txGroup.getList());


        //事务不满足直接回滚事务
        if (!checkState) {
            transaction(txGroup.getList(), 0);
            return;
        }
        txGroup.setState(1);


        boolean hasOvertime = txManagerService.getHasOvertime(txGroup);

        if (isOk) {
            if(hasOvertime){
                transaction(txGroup.getList(), -1);
            }else{
                //提交事务
               boolean hasOk =  transaction(txGroup.getList(), 1);
               txManagerService.dealTxGroup(txGroup,hasOk);
            }
        } else {
            if(hasOvertime){
                transaction(txGroup.getList(), -1);
            }else{
                transaction(txGroup.getList(), 0);
            }
        }


    }


    /**
     * 检查事务是否提交
     *
     * @param list
     */
    private boolean reloadChannel(List<TxInfo> list) {
        int count = 0;
        for (TxInfo info : list) {
            Channel channel = SocketManager.getInstance().getChannelByModelName(info.getModelName());
            if (channel != null) {
                if (channel.isActive()) {
                    info.setChannel(channel);
                    count++;
                }
            }
        }
        return count == list.size();
    }


    private void awaitSend(ExecuteAwaitTask awaitTask, TxInfo txInfo,JSONObject jsonObject){
        if(awaitTask.getState()==1){
            SocketUtils.sendMsg( txInfo.getChannel(),jsonObject.toString());
        }else{
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            awaitSend(awaitTask, txInfo, jsonObject);
        }
    }

    /**
     * 事务提交或回归
     *
     * @param list
     * @param checkSate
     */
    private boolean transaction(List<TxInfo> list, final int checkSate) {
        CountDownLatchHelper<Boolean> countDownLatchHelper = new CountDownLatchHelper<>();
        for (final TxInfo txInfo : list) {
            countDownLatchHelper.addExecute(new IExecute<Boolean>() {
                @Override
                public Boolean execute() {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("a", "t");
                    jsonObject.put("c", checkSate);
                    jsonObject.put("t", txInfo.getKid());
                    String key = KidUtils.generateShortUuid();
                    jsonObject.put("k", key);
                    final Task task = ConditionUtils.getInstance().createTask(key);

                    executorService.schedule(new Runnable() {
                        @Override
                        public void run() {
                            task.setBack(new IBack() {
                                @Override
                                public Object doing(Object... objs) throws Throwable {
                                    return "-2";
                                }
                            });
                            task.signalTask();
                        }
                    }, 1, TimeUnit.SECONDS);

                    final ExecuteAwaitTask awaitTask = new ExecuteAwaitTask();

                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            awaitSend(awaitTask,txInfo,jsonObject);
                        }
                    });
                    task.awaitTask(new IBack() {
                        @Override
                        public Object doing(Object... objects) throws Throwable {
                            awaitTask.setState(1);
                            return null;
                        }
                    });
                    try {
                        String data = (String) task.getBack().doing();
                        // 1  成功 0 失败 -1 task为空 -2 超过
                        boolean res =  "1".equals(data);

                        if("1".equals(data)||"0".equals(data)){
                            txInfo.setNotify(1);
                        }

                        return res;
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        return false;
                    } finally {
                        task.remove();
                    }
                }
            });
        }

        List<Boolean> hasOks = countDownLatchHelper.execute().getData();
        for (boolean bl : hasOks) {
            if (bl == false) {
                return false;
            }
        }
        return true;
    }


//    private boolean lock(List<TxInfo> list) {
//        for (final TxInfo txInfo : list) {
//            CountDownLatchHelper<Boolean> countDownLatchHelper = new CountDownLatchHelper<>();
//            countDownLatchHelper.addExecute(new IExecute<Boolean>() {
//                @Override
//                public Boolean execute() {
//                    JSONObject jsonObject = new JSONObject();
//                    jsonObject.put("a", "l");
//                    jsonObject.put("t", txInfo.getKid());
//                    String key = KidUtils.generateShortUuid();
//                    jsonObject.put("k", key);
//                    final Task task = ConditionUtils.getInstance().createTask(key);
//                    SocketUtils.sendMsg( txInfo.getChannel(),jsonObject.toString());
//                    Constant.scheduledExecutorService.schedule(new Runnable() {
//                        @Override
//                        public void run() {
//                            task.setBack(new IBack() {
//                                @Override
//                                public Object doing(Object... objs) throws Throwable {
//                                    return "0";
//                                }
//                            });
//                            task.signalTask();
//                        }
//                    }, 1, TimeUnit.SECONDS);
//                    task.awaitTask();
//                    try {
//                        String data = (String) task.getBack().doing();
//                        return "1".equals(data);
//                    } catch (Throwable throwable) {
//                        throwable.printStackTrace();
//                    } finally {
//                        task.remove();
//                    }
//                    return false;
//                }
//            });
//            List<Boolean> isLocks = countDownLatchHelper.execute().getData();
//            for (boolean bl : isLocks) {
//                if (bl == false) {
//                    return false;
//                }
//            }
//
//        }
//
//        return true;
//    }


}
