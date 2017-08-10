package com.lorne.tx.thread;

/**
 * create by lorne on 2017/8/9
 */
public abstract class HookRunnable implements Runnable {

    private volatile boolean hasOver;

    @Override
    public void run() {

        Thread thread = new Thread(){
            @Override
            public void run() {
                while (!hasOver){}
            }
        };

        Runtime.getRuntime().addShutdownHook(thread);

        try {
            run0();
        }finally {
            hasOver = true;
            if (!thread.isAlive()) {
                Runtime.getRuntime().removeShutdownHook(thread);
            }
        }
    }

    public abstract void run0();

}
