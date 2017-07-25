package com.lorne.tx.mq.model;

import com.lorne.core.framework.model.JsonModel;
import io.netty.channel.Channel;

/**
 * Created by lorne on 2017/6/7.
 */
public class TxInfo extends JsonModel {

    private String kid;

    private String modelName;

    private int notify;

    /**
     * 0 不组合
     * 1 组合
     */
    private int isGroup;

    private Channel channel;


    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getNotify() {
        return notify;
    }

    public void setNotify(int notify) {
        this.notify = notify;
    }

    public int getIsGroup() {
        return isGroup;
    }

    public void setIsGroup(int isGroup) {
        this.isGroup = isGroup;
    }
}
