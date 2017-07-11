package com.lorne.tx.dubbo.filter;

import com.alibaba.dubbo.rpc.*;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.service.impl.CompensateServiceImpl;

/**
 * Created by lorne on 2017/6/30.
 */
public class TransactionFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        if(txTransactionLocal!=null){
            if(txTransactionLocal.isHasCompensate()){
                RpcContext.getContext().setAttachment("tx-group", CompensateServiceImpl.COMPENSATE_KEY);
            }else{
                RpcContext.getContext().setAttachment("tx-group",txTransactionLocal.getGroupId());
            }
        }
        return invoker.invoke(invocation);
    }
}
