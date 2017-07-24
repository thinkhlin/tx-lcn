package com.lorne.tx.mq.model;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;

public class NotifyMsg {

    private int state;
    private long nowTime;

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getNowTime() {
        return nowTime;
    }

    public void setNowTime(long nowTime) {
        this.nowTime = nowTime;
    }


    public static NotifyMsg parser(String json) {
        try {
            if(StringUtils.isEmpty(json)){
                return null;
            }
            JSONObject jsonObject = JSONObject.parseObject(json);
            NotifyMsg notifyMsg = new NotifyMsg();
            notifyMsg.setState(jsonObject.getInteger("s"));
            notifyMsg.setNowTime(jsonObject.getLong("n"));
            return notifyMsg;

        } catch (Exception e) {
            return null;
        }
    }

    public String toJsonString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("s", getState());
        jsonObject.put("n",getNowTime());
        return jsonObject.toString();
    }

}
