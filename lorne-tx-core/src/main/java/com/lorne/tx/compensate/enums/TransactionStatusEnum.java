package com.lorne.tx.compensate.enums;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 *  事务状态
 * @author yu.xiao @happylifeplat.com
 * @version 1.0
 * @since JDK 1.8
 */
public enum TransactionStatusEnum {

    /**
     * Begin transaction status enum.
     */
    BEGIN,
    /**
     * Commit transaction status enum.
     */
    COMMIT,
    /**
     * Rollback transaction status enum.
     */
    ROLLBACK,
    /**
     * Failure transaction status enum.
     */
    FAILURE
}
