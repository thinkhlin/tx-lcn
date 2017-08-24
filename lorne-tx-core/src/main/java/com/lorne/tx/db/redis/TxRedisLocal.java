package com.lorne.tx.db.redis;

import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.Task;

/**
 * create by lorne on 2017/8/22
 */
public class TxRedisLocal {

    private final static ThreadLocal<TxRedisLocal> currentLocal = new ThreadLocal<TxRedisLocal>();

    private Task task;



    public static TxRedisLocal current() {
        return currentLocal.get();
    }

    public static void setCurrent(TxRedisLocal current) {
        currentLocal.set(current);
    }

    public TxRedisLocal() {
       String taskId = KidUtils.getKid();
       task= ConditionUtils.getInstance().createTask(taskId);
    }

    public Task getTask() {
        return task;
    }

    private int state = 0;

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
}
