package com.lorne.tx.mq.model;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lorne on 2017/6/7.
 */
public class TxGroup {

    private String groupId;

    private boolean hasOver = false;

    private long startTime;

    private long endTime;

    private long nowTime;

    private int state;




    public boolean isHasOver() {
        return hasOver;
    }

    public void hasOvered() {
        this.hasOver = true;
    }

    private List<TxInfo> list;

    public TxGroup() {
        list = new ArrayList<>();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public List<TxInfo> getList() {
        return list;
    }

    public void setHasOver(boolean hasOver) {
        this.hasOver = hasOver;
    }

    public void setList(List<TxInfo> list) {
        this.list = list;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public void addTransactionInfo(TxInfo info) {
        if (!hasOver) {
            list.add(info);
        }
    }

    public long getNowTime() {
        return nowTime;
    }

    public void setNowTime(long nowTime) {
        this.nowTime = nowTime;
    }



    public static TxGroup parser(String json) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(json);
            TxGroup txGroup = new TxGroup();
            txGroup.setGroupId(jsonObject.getString("g"));
            txGroup.setHasOver(jsonObject.getInteger("ho") == 1);
            txGroup.setStartTime(jsonObject.getLong("st"));
            txGroup.setEndTime(jsonObject.getLong("et"));
            txGroup.setNowTime(jsonObject.getLong("nt"));
            txGroup.setState(jsonObject.getInteger("s"));
            JSONArray array = jsonObject.getJSONArray("l");
            int length = array.size();
            for (int i = 0; i < length; i++) {
                JSONObject object = array.getJSONObject(i);
                TxInfo info = new TxInfo();
                info.setKid(object.getString("k"));
                info.setModelName(object.getString("m"));
                info.setNotify(object.getInteger("n"));
                info.setIsGroup(object.getInteger("ig"));
                info.setAddress(object.getString("a"));
                txGroup.getList().add(info);
            }
            return txGroup;

        } catch (Exception e) {
            return null;
        }

    }

    public String toJsonString(boolean noList) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("g", getGroupId());
        jsonObject.put("ho", hasOver ? 1 : 0);
        jsonObject.put("st", getStartTime());
        jsonObject.put("et", getEndTime());
        jsonObject.put("nt", getNowTime());
        jsonObject.put("s", getState());
        if(noList) {
            JSONArray jsonArray = new JSONArray();
            for (TxInfo info : getList()) {
                JSONObject item = new JSONObject();
                item.put("k", info.getKid());
                item.put("m", info.getModelName());
                item.put("n", info.getNotify());
                item.put("ig", info.getIsGroup());
                item.put("a", info.getAddress());
                jsonArray.add(item);
            }
            jsonObject.put("l", jsonArray);
        }
        return jsonObject.toString();
    }

    public String toJsonString() {
        return toJsonString(true);
    }
}
