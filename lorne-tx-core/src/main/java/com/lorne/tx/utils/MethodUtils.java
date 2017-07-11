package com.lorne.tx.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Created by yuliang on 2017/7/11.
 */
public class MethodUtils {

    private static final Logger logger = LoggerFactory.getLogger(MethodUtils.class);

    public static boolean invoke(ApplicationContext spring,String className,String methodName,Object... args){
        try {
            Class clz = Class.forName(className);
            Object  bean = spring.getBean(clz);
            Object res = org.apache.commons.lang.reflect.MethodUtils.invokeMethod(bean,methodName,args);
            System.out.println(res);
            logger.info("invoke -> className:"+className+",methodName::"+methodName+",args:"+args+",res:"+res);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
