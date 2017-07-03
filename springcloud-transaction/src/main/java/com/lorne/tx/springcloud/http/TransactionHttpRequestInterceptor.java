package com.lorne.tx.springcloud.http;

import com.lorne.tx.bean.TxTransactionLocal;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Created by lorne on 2017/7/3.
 */
public class TransactionHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        String groupId = txTransactionLocal==null?null:txTransactionLocal.getGroupId();
        request.getHeaders().add("tx-group",groupId);
        return execution.execute(request,body);
    }
}
