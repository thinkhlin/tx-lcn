

package com.lorne.tx.enums;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 *  事务类型
 * @author yu.xiao @happylifeplat.com
 * @version 1.0
 * @since JDK 1.8
 */
public enum TransactionTypeEnum {

    ROOT(1),
    BRANCH(2);

    int id;

    TransactionTypeEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public  static TransactionTypeEnum  valueOf(int id) {
        switch (id) {
            case 1:
                return ROOT;
            case 2:
                return BRANCH;
            default:
                return null;
        }
    }

}
