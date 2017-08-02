package com.lorne.tx.mq.model;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 * Created by lorne on 2017/6/7.
 */
public class TxGroup {

    private String groupId;

    private boolean hasOver = false;

    private int waitTime;

    private long startTime;

    private long nowTime;

    public boolean isHasOver() {
        return hasOver;
    }

    public void hasOvered() {
        this.hasOver = true;
    }


    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }


    public void setHasOver(boolean hasOver) {
        this.hasOver = hasOver;
    }


    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }


    public long getNowTime() {
        return nowTime;
    }

    public void setNowTime(long nowTime) {
        this.nowTime = nowTime;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }


    public static TxGroup parser(String json) {
        try {
            if (StringUtils.isEmpty(json)) {
                return null;
            }
            JSONObject jsonObject = JSONObject.parseObject(json);
            TxGroup txGroup = new TxGroup();
            txGroup.setGroupId(jsonObject.getString("g"));
            txGroup.setHasOver(jsonObject.getInteger("ho") == 1);
            txGroup.setWaitTime(jsonObject.getInteger("w"));
            txGroup.setStartTime(jsonObject.getLong("st"));
            txGroup.setNowTime(jsonObject.getLong("nt"));
            return txGroup;

        } catch (Exception e) {
            return null;
        }

    }

    public String toJsonString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", getGroupId());
        jsonObject.put("ho", hasOver ? 1 : 0);
        jsonObject.put("w", getWaitTime());
        jsonObject.put("st", getStartTime());
        jsonObject.put("nt", getNowTime());
        JSONArray jsonArray = new JSONArray();
        jsonObject.put("l", jsonArray);
        return jsonObject.toString();
    }
}
