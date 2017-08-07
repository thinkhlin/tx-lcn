package com.lorne.tx.service.impl;


import com.lorne.core.framework.utils.KidUtils;
import com.lorne.tx.Constants;
import com.lorne.tx.service.TransactionConfirmService;
import com.lorne.tx.service.TxManagerService;
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

import java.util.concurrent.TimeUnit;

/**
 * Created by lorne on 2017/6/7.
 */
@Service
public class TxManagerServiceImpl implements TxManagerService {

    @Value("${redis_save_max_time}")
    private int redis_save_max_time;

    @Value("${transaction_netty_delay_time}")
    private int transaction_netty_delay_time;


    private final static String key_prefix = "tx_manager_default_";

    private final static String key_prefix_notify = "tx_manager_notify_";


    @Autowired
    private RedisTemplate<String, String> redisTemplate;


    @Autowired
    private TransactionConfirmService transactionConfirmService;

    private Logger logger = LoggerFactory.getLogger(TxManagerServiceImpl.class);


    @Override
    public TxGroup createTransactionGroup(String taskId,String modelName) {
        String groupId = KidUtils.generateShortUuid();
        TxGroup txGroup = new TxGroup();
        txGroup.setStartTime(System.currentTimeMillis());
        txGroup.setGroupId(groupId);
        String key = key_prefix + groupId;
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        value.set(key, txGroup.toJsonString(), redis_save_max_time, TimeUnit.SECONDS);
        return txGroup;
    }

    @Override
    public TxGroup addTransactionGroup(String groupId, String taskId,int isGroup, String modelName) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        TxGroup txGroup = TxGroup.parser(json);
        if (txGroup != null) {
            TxInfo txInfo = new TxInfo();
            txInfo.setModelName(modelName);
            txInfo.setKid(taskId);
            txInfo.setAddress(Constants.address);
            txInfo.setIsGroup(isGroup);
            txGroup.addTransactionInfo(txInfo);
            value.set(key, txGroup.toJsonString(), redis_save_max_time, TimeUnit.SECONDS);
            return txGroup;
        }
        return null;
    }

    @Override
    public  boolean checkTransactionGroup(String groupId, String taskId) {
        logger.info("checkTransactionGroup->groupId:"+groupId+",taskId:"+taskId);
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            key = key_prefix_notify + groupId;
            json = value.get(key);
            if (StringUtils.isEmpty(json)) {
                return false;
            }
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
            if (info.getIsGroup()==0&&info.getNotify() == 0) {
                isOver = false;
                break;
            }
        }
        if (isOver) {
            if(key.startsWith(key_prefix_notify)) {
                redisTemplate.delete(key);
            } else {
                value.set(key, txGroup.toJsonString());
            }
        }
        logger.info("end-checkTransactionGroup->groupId:"+groupId+",taskId:"+taskId+",res:"+res);
        return res;
    }


    @Override
    public boolean checkTransactionGroupState(String groupId) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            key = key_prefix_notify + groupId;
            json = value.get(key);
            if (StringUtils.isEmpty(json)) {
                return false;
            }
        }
        TxGroup txGroup = TxGroup.parser(json);
        return txGroup.getState() == 1;
    }

    @Override
    public boolean closeTransactionGroup(String groupId,int state) {
        ValueOperations<String, String> value = redisTemplate.opsForValue();
        String key = key_prefix + groupId;
        String json = value.get(key);
        if (StringUtils.isEmpty(json)) {
            return false;
        }
        final TxGroup txGroup = TxGroup.parser(json);
        txGroup.hasOvered();
        txGroup.setState(state);
        txGroup.setEndTime(System.currentTimeMillis());
        transactionConfirmService.confirm(txGroup);
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
    public void deleteTxGroup(TxGroup txGroup) {
        String key = key_prefix + txGroup.getGroupId();
        redisTemplate.delete(key);
    }

    @Override
    public int getDelayTime() {
        return transaction_netty_delay_time;
    }
}
