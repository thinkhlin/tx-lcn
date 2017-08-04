package com.lorne.tx.dubbo.service.impl;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.remoting.exchange.ExchangeServer;
import com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol;
import com.lorne.core.framework.utils.encode.MD5Util;
import com.lorne.tx.service.ModelNameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class ModelNameServiceImpl implements ModelNameService {


    @Autowired
    private  ApplicationConfig applicationConfig;

    @Override
    public String getModelName() {
        String modelName = applicationConfig.getName();
        DubboProtocol dubboProtocol =  DubboProtocol.getDubboProtocol();
        Collection<ExchangeServer> servers =  dubboProtocol.getServers();
        String address = "";
        for(ExchangeServer server:servers){
            address+=server.getLocalAddress();
        }
        address =  MD5Util.md5(address);
        return address+"_"+modelName;
    }
}
