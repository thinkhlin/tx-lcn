package com.lorne.tx.manager.service.impl;


import com.lorne.core.framework.utils.KidUtils;
import com.lorne.tx.manager.service.TransactionConfirmService;
import com.lorne.tx.manager.service.TxManagerService;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.model.TxInfo;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by lorne on 2017/6/7.
 */
@Service
public class TxManagerServiceImpl implements TxManagerService {

    @Value("${redis_save_max_time}")
    private int redis_save_max_time;

    @Value("${transaction_wait_max_time}")
    private int transaction_wait_max_time;

    private final static String key_prefix = "tx_manager_default_";

    private final static String key_prefix_notify = "tx_manager_notify_";

    //网络消耗
    private final  static  long dt = 500;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Executor threadPool = Executors.newFixedThreadPool(100);

    @Autowired
    private TransactionConfirmService transactionConfirmService;

    private Logger logger = LoggerFactory.getLogger(TxManagerServiceImpl.class);


    @Override
    public TxGroup createTransactionGroup() {
        String groupId = KidUtils.generateShortUuid();
        TxGroup txGroup = new TxGroup();
        txGroup.setStartTime(System.currentTimeMillis());
        txGroup.setGroupId(groupId);
        txGroup.setWaitTime(transaction_wait_max_time);
        String key = key_prefix + groupId;
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        value.set(key, txGroup.toJsonString(), redis_save_max_time, TimeUnit.SECONDS);
        return txGroup;
    }

    @Override
    public TxGroup addTransactionGroup(String groupId, String taskId, String modelName) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        TxGroup txGroup = TxGroup.parser(json);
        long now = System.currentTimeMillis();
        double time = (now - txGroup.getStartTime() - dt) / 1000;
        if(time > transaction_wait_max_time){
            //事务超时，返回失败
            return null;
        }

        if (txGroup != null) {
            TxInfo txInfo = new TxInfo();
            txInfo.setModelName(modelName);
            txInfo.setKid(taskId);

            txGroup.addTransactionInfo(txInfo);
            value.set(key, txGroup.toJsonString(), redis_save_max_time, TimeUnit.SECONDS);
            return txGroup;
        }
        return null;
    }

    @Override
    public boolean checkTransactionGroup(String groupId, String taskId) {
        logger.info("checkTransactionGroup->groupId:"+groupId+",taskId:"+taskId);
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix_notify + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return false;
        }
        TxGroup txGroup = TxGroup.parser(json);
        boolean res = txGroup.getState() == 1;

        for (TxInfo info : txGroup.getList()) {
            if (info.getKid().equals(taskId)) {
                info.setNotify(1);
            }
        }

        boolean isOver = true;
        for (TxInfo info : txGroup.getList()) {
            if (info.getNotify() == 0) {
                isOver = false;
                break;
            }
        }

        if (isOver) {
            redisTemplate.delete(key);
        } else {
            value.set(key, txGroup.toJsonString());
        }
        logger.info("end-checkTransactionGroup->groupId:"+groupId+",taskId:"+taskId+",res:"+res);
        return res;
    }


    @Override
    public boolean checkTransactionGroupState(String groupId) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix_notify + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return false;
        }
        TxGroup txGroup = TxGroup.parser(json);
        return txGroup.getState() == 1;
    }

    @Override
    public boolean closeTransactionGroup(String groupId) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return false;
        }
        final TxGroup txGroup = TxGroup.parser(json);
        txGroup.hasOvered();
        txGroup.setEndTime(System.currentTimeMillis());
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                transactionConfirmService.confirm(txGroup);
            }
        });
        return true;
    }


    @Override
    public boolean notifyTransactionInfo(String groupId, String kid, boolean state) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return false;
        }
        TxGroup txGroup = TxGroup.parser(json);
        List<TxInfo> list = txGroup.getList();
        for (TxInfo info : list) {
            if (info.getKid().equals(kid)) {
                info.setState(state ? 1 : 0);
            }
        }
        value.set(key, txGroup.toJsonString(), redis_save_max_time, TimeUnit.SECONDS);
        return true;
    }


    @Override
    public void dealTxGroup(TxGroup txGroup, boolean hasOk) {
        String key = key_prefix + txGroup.getGroupId();
        if (!hasOk) {
            //未通知成功

            if (txGroup.getState() == 1) {
                ValueOperations<String, String> value = redisTemplate.opsForValue();
                String newKey = key_prefix_notify + txGroup.getGroupId();
                value.set(newKey, txGroup.toJsonString());
            }

        }
        redisTemplate.delete(key);
    }


    @Override
    public boolean getHasOvertime(TxGroup txGroup) {

        double time = (txGroup.getEndTime() - txGroup.getStartTime() - dt) / 1000;
        return time > transaction_wait_max_time;
    }
}
