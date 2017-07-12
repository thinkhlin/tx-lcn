package com.lorne.tx.repository;

import com.lorne.tx.bean.TransactionRecover;

import java.util.List;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 *  定义事务恢复资源接口
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @since JDK 1.8
 */
public interface TransactionRecoverRepository {

    /**
     * 创建本地事务对象
     * @param transactionRecover 事务对象
     * @return rows
     */
    int create(TransactionRecover transactionRecover);

    /**
     * 删除对象
     * @param transactionRecover 事务对象
     * @return rows
     */
    int remove(TransactionRecover transactionRecover);


    /**
     * 更改事务对象
     * @param transactionRecover 事务对象
     * @return rows
     */
    int update(TransactionRecover transactionRecover);

    /**
     * 根据id获取对象
     * @param id 主键id
     * @return TransactionRecover
     */
    TransactionRecover findById(String id);

    /**
     * 获取需要提交的事务
     * @return  List<TransactionRecover>
     */
    List<TransactionRecover> listAll();
}
