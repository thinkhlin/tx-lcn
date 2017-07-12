package com.lorne.tx.compensate.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lorne.tx.compensate.model.TransactionRecover;
import com.lorne.tx.exception.TransactionRuntimeException;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>Description: .</p>
 * <p>Copyright: 2015-2017 happylifeplat.com All Rights Reserved</p>
 *  资源模板类
 * @author yu.xiao@happylifeplat.com
 * @version 1.0
 * @since JDK 1.8
 */
public abstract class CacheTransactionRecoverRepository  implements TransactionRecoverRepository  {

    private int expireDuration = 120;

    private Cache<String,TransactionRecover> transactionRecoverCache;


    public abstract int doCreate(TransactionRecover transactionRecover);

    public abstract int doUpdate(TransactionRecover transactionRecover);

    public abstract int doDelete(TransactionRecover transactionRecover);

    public abstract int doDelete(String id);

    public abstract TransactionRecover doFindOne(String id);

    public abstract List<TransactionRecover> doListAll();

    public CacheTransactionRecoverRepository() {
        transactionRecoverCache = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, TimeUnit.SECONDS).maximumSize(1000).build();
    }

    public void putToCache(TransactionRecover transactionRecover) {
        transactionRecoverCache.put(transactionRecover.getId(), transactionRecover);
    }

    public void removeFromCache(TransactionRecover transactionRecover) {
        transactionRecoverCache.invalidate(transactionRecover.getId());
    }

    public TransactionRecover findFromCache(String id) {
        return transactionRecoverCache.getIfPresent(id);
    }


    @Override
    public int create(TransactionRecover transactionRecover) {
        int result = doCreate(transactionRecover);
        if (result > 0) {
            putToCache(transactionRecover);
        }
        return result;
    }

    @Override
    public int update(TransactionRecover transactionRecover) {
        int result = 0;

        try {
            result = doUpdate(transactionRecover);
            if (result > 0) {
                putToCache(transactionRecover);
            } else {
                throw new TransactionRuntimeException();
            }
        } finally {
            if (result <= 0) {
                removeFromCache(transactionRecover);
            }
        }

        return result;
    }

    @Override
    public int remove(TransactionRecover transactionRecover) {
        int result;

        try {
            result = doDelete(transactionRecover);

        } finally {
            removeFromCache(transactionRecover);
        }
        return result;
    }

    @Override
    public TransactionRecover findById(String id) {
        TransactionRecover tccTransaction = findFromCache(id);

        if (tccTransaction == null) {
            tccTransaction = doFindOne(id);

            if (tccTransaction != null) {
                putToCache(tccTransaction);
            }
        }

        return tccTransaction;
    }

    @Override
    public List<TransactionRecover> listAll() {

        List<TransactionRecover> transactions = doListAll();

        for (TransactionRecover transaction : transactions) {
            putToCache(transaction);
        }

        return transactions;
    }


    public int getExpireDuration() {
        return expireDuration;
    }

    public void setExpireDuration(int expireDuration) {
        this.expireDuration = expireDuration;
    }




}
