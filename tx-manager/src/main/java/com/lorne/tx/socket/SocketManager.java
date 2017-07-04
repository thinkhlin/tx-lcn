package com.lorne.tx.socket;

import com.lorne.tx.Constants;
import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by lorne on 2017/6/30.
 */
public class SocketManager {

    /**
     * 最大连接数
     */
    private int maxConnection = Constants.maxConnection;

    /**
     * 当前连接数
     */
    private int nowConnection;

    /**
     * 允许连接请求 true允许 false拒绝
     */
    private boolean allowConnection = true;

    private List<Channel> clients = null;

    private static SocketManager manager = null;

    public  synchronized static SocketManager getInstance() {
        if (manager == null)
            manager = new SocketManager();
        return manager;
    }


    public Channel getChannelByModelName(String name){
        for(Channel channel:clients){
            String modelName =channel.remoteAddress().toString();

            if(modelName.equals(name)){
                return channel;
            }
        }
        return null;
    }

    private  SocketManager() {
        clients = new CopyOnWriteArrayList<Channel>();
    }

    public void addClient(Channel client) {
        clients.add(client);
        nowConnection = clients.size();

        allowConnection = (maxConnection!=nowConnection);
    }

    public void removeClient(Channel client) {
        clients.remove(client);
        nowConnection = clients.size();

        allowConnection = (maxConnection!=nowConnection);
    }


    public int getMaxConnection() {
        return maxConnection;
    }

    public int getNowConnection() {
        return nowConnection;
    }

    public boolean isAllowConnection() {
        return allowConnection;
    }
}
