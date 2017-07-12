package com.lorne.tx.compensate.model;

import com.lorne.tx.compensate.enums.TransactionStatusEnum;
import com.lorne.tx.compensate.enums.TransactionTypeEnum;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 * 本地恢复实体事务bean
 *
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @date 2017/7/12 10:02
 * @since JDK 1.8
 */
public class TransactionRecover implements Serializable {


    /**
     * 主键id
     */
    private String id;


    /**
     * 重试次数，
     */
    private int retriedCount = 0;

    /**
     * 创建时间
     */
    private Date createTime = new Date();


    /**
     * 版本控制 防止并发问题
     */
    private int version = 1;

    /**
     * 事务组id
     */
    private String groupId;

    /**
     * 任务id
     */
    private String taskId;


    /**
     * 事务执行方法
     */
    private TransactionInvocation invocation;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public int getRetriedCount() {
        return retriedCount;
    }

    public void setRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public TransactionInvocation getInvocation() {
        return invocation;
    }

    public void setInvocation(TransactionInvocation invocation) {
        this.invocation = invocation;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
