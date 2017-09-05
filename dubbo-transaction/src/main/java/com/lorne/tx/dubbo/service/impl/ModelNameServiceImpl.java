package com.lorne.tx.dubbo.service.impl;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProviderConfig;
import com.lorne.tx.service.ModelNameService;
import org.apache.commons.codec.digest.Md5Crypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by lorne on 2017/7/12.
 */
@Service
public class ModelNameServiceImpl implements ModelNameService {


    @Autowired
    private  ApplicationConfig applicationConfig;

    @Autowired
    private ProviderConfig providerConfig;


    @Override
    public String getModelName() {
        return  applicationConfig.getName();
    }

    private String getIp(){
        String host = null;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return host;
    }

    @Override
    public String getUniqueKey() {
        String address = getIp()+providerConfig.getPort();
        try {
            return Md5Crypt.md5Crypt(address.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }


}
