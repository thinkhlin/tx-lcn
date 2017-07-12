package com.lorne.tx.springcloud.service.impl;

import com.lorne.tx.service.ModelNameService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
@Configuration
public class ModelNameServiceImpl implements ModelNameService {

    @Value("${spring.application.name}")
    private String modelName;


    @Override
    public String getModelName() {
        return modelName;
    }
}
