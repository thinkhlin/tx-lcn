package com.lorne.tx.service.impl;

import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.service.CompensateService;
import org.springframework.stereotype.Service;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class CompensateServiceImpl implements CompensateService {


    public final static String COMPENSATE_KEY = "COMPENSATE";

    @Override
    public void start() {
        // TODO: 2017/7/11  查找补偿数据


        //在各业务模块执行远程调用的时候判断一下groupId类型
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        if(txTransactionLocal!=null){
            txTransactionLocal.setHasCompensate(true);
            TxTransactionLocal.setCurrent(txTransactionLocal);
        }

        // TODO: 2017/7/11  执行补偿业务 （只要业务执行未出现异常就算成功）


    }


    @Override
    public String saveTransactionInfo(String className, String methodName, String groupId, String taskId, Object... args) {
        // TODO: 2017/7/11  记录补偿数据
        return null;
    }

    @Override
    public boolean deleteTransactionInfo(String id) {
        //TODO: 2017/7/11  删除补偿数据
        return false;
    }
}
