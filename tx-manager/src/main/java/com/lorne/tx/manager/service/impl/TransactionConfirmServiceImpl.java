package com.lorne.tx.manager.service.impl;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.core.framework.utils.thread.CountDownLatchHelper;
import com.lorne.core.framework.utils.thread.IExecute;
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
import java.util.concurrent.*;


/**
 * Created by lorne on 2017/6/9.
 */
@Service
public class TransactionConfirmServiceImpl implements TransactionConfirmService {


    private Logger logger = LoggerFactory.getLogger(TransactionConfirmServiceImpl.class);

    private ScheduledExecutorService executorService  = Executors.newScheduledThreadPool(100);

    private Executor threadPool = Executors.newFixedThreadPool(100);

    @Autowired
    private TxManagerService txManagerService;

    @Override
    public void confirm(TxGroup txGroup) {
      //  logger.info("end:" + txGroup.toJsonString());
    //    boolean checkState = true;


//        //检查事务是否正常
//        for (TxInfo info : txGroup.getList()) {
//            if (info.getState() == 0) {
//                checkState = false;
//            }
//        }



        //绑定管道对象，检查网络
        boolean isOk = reloadChannel(txGroup.getList());


        //事务不满足直接回滚事务
        if (txGroup.getState()==0) {
            transaction(txGroup, 0);
            return;
        }
//        txGroup.setState(1);


        boolean hasOvertime = txManagerService.getHasOvertime(txGroup);

        if (isOk) {
            if(hasOvertime){
                transaction(txGroup, -1);
            }else{
                //提交事务
               boolean hasOk =  transaction(txGroup, 1);
               txManagerService.dealTxGroup(txGroup,hasOk);
            }
        } else {
            if(hasOvertime){
                transaction(txGroup, -1);
            }else{
                transaction(txGroup, 0);
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


    private void awaitSend(Task task, TxInfo txInfo,JSONObject jsonObject){
        if(task.isAwait()){
            SocketUtils.sendMsg( txInfo.getChannel(),jsonObject.toString());
        }else{
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            awaitSend(task, txInfo, jsonObject);
        }
    }

    /**
     * 事务提交或回归
     *
     * @param checkSate
     */
    private boolean transaction(TxGroup txGroup, final int checkSate) {
        CountDownLatchHelper<Boolean> countDownLatchHelper = new CountDownLatchHelper<>();
        for (final TxInfo txInfo : txGroup.getList()) {
            if(txInfo.getIsGroup()==0) {
                countDownLatchHelper.addExecute(new IExecute<Boolean>() {
                    @Override
                    public Boolean execute() {
                        final JSONObject jsonObject = new JSONObject();
                        jsonObject.put("a", "t");
                        jsonObject.put("c", checkSate);
                        jsonObject.put("t", txInfo.getKid());
                        String key = KidUtils.generateShortUuid();
                        jsonObject.put("k", key);
                        final Task task = ConditionUtils.getInstance().createTask(key);

                        ScheduledFuture future = executorService.schedule(new Runnable() {
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
                        }, txManagerService.getDelayTime() * 3, TimeUnit.SECONDS);

                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                awaitSend(task, txInfo, jsonObject);
                            }
                        });
                        task.awaitTask();

                        if (!future.isDone()) {
                            future.cancel(false);
                        }


                        try {
                            String data = (String) task.getBack().doing();
                            // 1  成功 0 失败 -1 task为空 -2 超过
                            boolean res = "1".equals(data);

                            if ("1".equals(data) || "0".equals(data)) {
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
        }

        List<Boolean> hasOks = countDownLatchHelper.execute().getData();

        boolean hasOk = true;
        for (boolean bl : hasOks) {
            if (bl == false) {
                hasOk =  false;
                break;
            }
        }
        logger.info("--->"+hasOk+",group:"+txGroup.getGroupId()+",state:"+checkSate+",list:" +txGroup.toJsonString());
        return hasOk;
    }


}
