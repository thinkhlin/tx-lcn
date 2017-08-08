package com.lorne.tx.service.impl;

import com.lorne.tx.mq.service.NettyService;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.service.InitService;
import com.lorne.tx.service.TimeOutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by yuliang on 2017/7/11.
 */
@Service
public class InitServiceImpl implements InitService {

    private final static Logger logger = LoggerFactory.getLogger(InitServiceImpl.class);

    @Autowired
    private NettyService nettyService;

    @Autowired
    private CompensateService compensateService;

    @Autowired
    private TimeOutService timeOutService;

    @Override
    public void start() {
        nettyService.start();
        logger.info("socket-start..");

        timeOutService.loadOutTime();

        compensateService.start();
        logger.info("check-compensate-running..");

    }
}
