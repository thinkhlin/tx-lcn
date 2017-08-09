package com.lorne.tx.thread;

/**
 * create by lorne on 2017/8/9
 */
public abstract class HookRunnable implements Runnable {

    private volatile boolean hasOver;

    @Override
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                while (!hasOver){}
            }
        });
        run0();
        hasOver = true;
    }

    public abstract void run0();
}
