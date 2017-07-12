package com.lorne.tx.dubbo.service.impl;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.lorne.tx.service.ModelNameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class ModelNameServiceImpl implements ModelNameService {


    @Autowired
    private  ApplicationConfig applicationConfig;

    @Override
    public String getModelName() {
        return applicationConfig.getName();
    }
}
