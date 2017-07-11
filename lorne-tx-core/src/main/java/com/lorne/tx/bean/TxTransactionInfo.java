package com.lorne.tx.bean;

import com.lorne.tx.annotation.TxTransaction;

import java.lang.reflect.Method;

/**
 * Created by lorne on 2017/6/8.
 */
public class TxTransactionInfo {


    private TxTransaction transaction;

    private TxTransactionLocal txTransactionLocal;

    private String txGroupId;

    private TransactionLocal transactionLocal;

    private String className;
    private String methodName;
    private Object[] args;


//    public TxTransactionInfo(TxTransaction transaction, TxTransactionLocal txTransactionLocal, String txGroupId, TransactionLocal transactionLocal) {
//        this.transaction = transaction;
//        this.txTransactionLocal = txTransactionLocal;
//        this.txGroupId = txGroupId;
//        this.transactionLocal = transactionLocal;
//    }

    public TxTransactionInfo(TxTransaction transaction, TxTransactionLocal txTransactionLocal, TransactionLocal transactionLocal, String className, String methodName, Object[] args) {
        this.transaction = transaction;
        this.txTransactionLocal = txTransactionLocal;
        this.transactionLocal = transactionLocal;
        this.className = className;
        this.methodName = methodName;
        this.args = args;
    }

    public TransactionLocal getTransactionLocal() {
        return transactionLocal;
    }

    public TxTransaction getTransaction() {
        return transaction;
    }

    public TxTransactionLocal getTxTransactionLocal() {
        return txTransactionLocal;
    }

    public String getTxGroupId() {
        return txGroupId;
    }


    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getArgs() {
        return args;
    }
}
