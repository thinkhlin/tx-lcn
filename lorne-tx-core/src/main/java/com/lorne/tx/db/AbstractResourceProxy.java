package com.lorne.tx.db;


import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.db.service.DataSourceService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * create by lorne on 2017/8/22
 */

public abstract class AbstractResourceProxy<C,T extends Resource> {


    protected Map<String, T> pools = new ConcurrentHashMap<>();


    @Autowired
    protected DataSourceService dataSourceService;


    public boolean hasGroup(String group){
        return pools.containsKey(group);
    }


    //default size
    protected volatile int maxCount = 5;

    //default time (seconds)
    protected int maxWaitTime = 30;

    protected volatile int nowCount = 0;

    // not thread
    protected ICallClose<T> subNowCount = new ICallClose<T>() {

        @Override
        public void close(T connection) {
            Task waitTask = connection.getWaitTask();
            if (waitTask != null) {
                if (!waitTask.isRemove()) {
                    waitTask.remove();
                }
            }

            pools.remove(connection.getGroupId());
            nowCount--;
        }
    };

    protected T loadConnection(){
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        if(txTransactionLocal==null){
            return null;
        }
        T old = pools.get(txTransactionLocal.getGroupId());
        if (old != null) {
            old.setHasIsGroup(true);
            old.addCompensate(txTransactionLocal.getRecover());

            txTransactionLocal.setHasIsGroup(true);
            TxTransactionLocal.setCurrent(txTransactionLocal);
            return old;
        }
        return null;
    }

    protected abstract C createLcnConnection(C connection, TxTransactionLocal txTransactionLocal);



    private C createConnection(TxTransactionLocal txTransactionLocal, C connection) throws SQLException {
        if (nowCount == maxCount) {
            for (int i = 0; i < maxWaitTime; i++) {
                for(int j=0;j<100;j++){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (nowCount < maxCount) {
                        return createLcnConnection(connection, txTransactionLocal);
                    }
                }
            }
        } else if (nowCount < maxCount) {
            return createLcnConnection(connection, txTransactionLocal);
        } else {
            throw new SQLException("connection was overload");
        }
        return connection;
    }



    protected C initLCNConnection(C connection) throws SQLException {
        C lcnConnection = connection;
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();

        if (txTransactionLocal != null
            && StringUtils.isNotEmpty(txTransactionLocal.getGroupId())) {
            if(TxTransactionCompensate.current()!=null){
                return connection;
            }else if (CompensateService.COMPENSATE_KEY.equals(txTransactionLocal.getGroupId())) {
                lcnConnection = createConnection(txTransactionLocal, connection);
            } else if (!txTransactionLocal.isHasStart()) {
                lcnConnection = createConnection(txTransactionLocal, connection);
            }

        }
        return lcnConnection;
    }



    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }



}
