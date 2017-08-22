package com.lorne.tx.db.task;

import com.lorne.core.framework.utils.task.ConditionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * create by lorne on 2017/8/22
 */
public class TaskGroupManager {

    private Map<String, TaskGroup> taskMap = new ConcurrentHashMap<String, TaskGroup>();

    private static TaskGroupManager instance = null;

    private TaskGroupManager(){}

    public static TaskGroupManager getInstance() {
        if (instance == null) {
            synchronized (TaskGroupManager.class) {
                if(instance==null){
                    instance = new TaskGroupManager();
                }
            }
        }
        return instance;
    }

    public TaskGroup createTask(String key,String type) {
        TaskGroup taskGroup = getTask(key);
        if(taskGroup==null){
            taskGroup = new TaskGroup();
        }
        taskGroup.setKey(key);

        String taskKey = type+"_"+key;

        TxTask task =  new TxTask(ConditionUtils.getInstance().createTask(taskKey));
        taskGroup.setCurrent(task);
        taskGroup.addTask(task);
        taskMap.put(key, taskGroup);
        return taskGroup;
    }

    public TaskGroup getTask(String key) {
        return taskMap.get(key);
    }


    public void removeKey(String key) {
        if (StringUtils.isNotEmpty(key)) {
            taskMap.remove(key);
        }

        System.out.println("taskMap->"+taskMap.size());
    }



}
