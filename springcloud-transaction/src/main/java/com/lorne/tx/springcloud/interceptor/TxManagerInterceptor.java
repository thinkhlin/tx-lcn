package com.lorne.tx.springcloud.interceptor;

import com.lorne.tx.bean.TxTransactionCompensate;
import com.lorne.tx.compensate.service.impl.CompensateServiceImpl;
import com.lorne.tx.service.AspectBeforeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by lorne on 2017/6/7.
 */

@Component
public class TxManagerInterceptor {


    @Autowired
    private AspectBeforeService aspectBeforeService;

    public Object around(ProceedingJoinPoint point) throws Throwable {

        TxTransactionCompensate compensate = TxTransactionCompensate.current();
        String groupId;
        if (compensate == null) {
            RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = requestAttributes == null ? null : ((ServletRequestAttributes) requestAttributes).getRequest();
            groupId = request == null ? null : request.getHeader("tx-group");
        } else {
            groupId = CompensateServiceImpl.COMPENSATE_KEY;
        }

        return aspectBeforeService.around(groupId, point);
    }
}
