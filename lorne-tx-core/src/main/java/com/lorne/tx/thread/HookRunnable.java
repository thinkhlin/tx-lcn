package com.lorne.tx.thread;

/**
 * create by lorne on 2017/8/9
 */
public abstract class HookRunnable implements Runnable {

    private volatile boolean hasOver;

    private static volatile boolean hasExit = false;

    @Override
    public void run() {

        Thread thread = new Thread(){
            @Override
            public void run() {
                hasExit = true;
                while (!hasOver){}
            }
        };
        if(!hasExit) {
            Runtime.getRuntime().addShutdownHook(thread);
        }else{
            System.out.println("jvm has exit..");
            return;
        }

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
