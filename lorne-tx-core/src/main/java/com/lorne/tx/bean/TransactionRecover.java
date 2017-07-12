package com.lorne.tx.bean;

import com.lorne.tx.enums.TransactionStatusEnum;
import com.lorne.tx.enums.TransactionTypeEnum;

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
     * 事务状态
     */
    private TransactionStatusEnum transactionStatusEnum;

    /**
     * 事务类型
     */
    private TransactionTypeEnum transactionTypeEnum;

    /**
     * 重试次数，
     */
    private int retriedCount = 0;

    /**
     * 创建时间
     */
    private Date createTime = new Date();

    /**
     * 最后更新时间
     */
    private Date lastUpdateTime = new Date();

    /**
     * 版本控制 防止并发问题
     */
    private int version = 1;


    /**
     * 事务执行方法
     */
    private TransactionInvocation transactionInvocation;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TransactionStatusEnum getTransactionStatusEnum() {
        return transactionStatusEnum;
    }

    public void setTransactionStatusEnum(TransactionStatusEnum transactionStatusEnum) {
        this.transactionStatusEnum = transactionStatusEnum;
    }

    public TransactionTypeEnum getTransactionTypeEnum() {
        return transactionTypeEnum;
    }

    public void setTransactionTypeEnum(TransactionTypeEnum transactionTypeEnum) {
        this.transactionTypeEnum = transactionTypeEnum;
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

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public TransactionInvocation getTransactionInvocation() {
        return transactionInvocation;
    }

    public void setTransactionInvocation(TransactionInvocation transactionInvocation) {
        this.transactionInvocation = transactionInvocation;
    }
}
