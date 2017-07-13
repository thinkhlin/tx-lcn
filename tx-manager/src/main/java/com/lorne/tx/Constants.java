package com.lorne.tx;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lorne on 2017/6/8.
 */
public class Constants {


    public static ExecutorService threadPool = null;


    public static int maxConnection;

    public static int socketPort;

    static {
        threadPool = Executors.newCachedThreadPool();
    }
}
