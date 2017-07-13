package com.lorne.tx.service.impl;


import com.lorne.tx.Constants;
import com.lorne.tx.eureka.DiscoveryService;
import com.lorne.tx.manager.service.TxManagerService;
import com.lorne.tx.model.TxServer;
import com.lorne.tx.model.TxState;
import com.lorne.tx.service.TxService;
import com.lorne.tx.socket.SocketManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lorne on 2017/7/1.
 */
@Service
public class TxServiceImpl implements TxService {

    @Value("${redis_save_max_time}")
    private int redis_save_max_time;

    @Value("${transaction_wait_max_time}")
    private int transaction_wait_max_time;

    @Autowired
    private TxManagerService managerService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryService discoveryService;


    @Override
    public TxServer getServer() {
        List<String> urls= getServices();
        List<TxState> states = new ArrayList<>();
        for(String url:urls){
            TxState state = restTemplate.getForObject(url+"/tx/manager/state",TxState.class);
            states.add(state);
        }
        if(states.size()<=1) {
            TxState state = getState();
            if (state.getMaxConnection() > state.getNowConnection()) {
                return TxServer.format(state);
            } else {
                return null;
            }
        }else{
            //找默认数据
            TxState state = getDefault(states,0);
            if (state == null) {
                //没有满足的默认数据
                return null;
            }
            int minNowConnection = state.getNowConnection();
            for (TxState s : states) {
                if (s.getMaxConnection() > s.getNowConnection()) {
                    if (s.getNowConnection() < minNowConnection) {
                        state = s;
                    }
                }
            }
            return TxServer.format(state);
        }
    }

    private TxState getDefault(List<TxState> states, int index) {
        TxState state = states.get(index);
        if (state.getMaxConnection() == state.getNowConnection()) {
            index++;
            if (states.size() - 1 >= index) {
                return getDefault(states, index);
            } else {
                return null;
            }
        } else {
            return state;
        }
    }


    @Override
    public TxState getState() {
        TxState state = new TxState();
        state.setIp(EurekaServerContextHolder.getInstance().getServerContext().getApplicationInfoManager().getEurekaInstanceConfig().getIpAddress());
        state.setPort(Constants.socketPort);
        state.setMaxConnection(SocketManager.getInstance().getMaxConnection());
        state.setNowConnection(SocketManager.getInstance().getNowConnection());
        state.setTransactionWaitMaxTime(transaction_wait_max_time);
        state.setRedisSaveMaxTime(redis_save_max_time);
        state.setSlbList(getServices());
        return state;
    }


    private List<String> getServices(){
        List<String> urls = new ArrayList<>();
        List<InstanceInfo> instanceInfos =discoveryService.getConfigServiceInstances();
        for (InstanceInfo instanceInfo : instanceInfos) {
            String url = instanceInfo.getHomePageUrl();
            urls.add(url);
        }
        return urls;
    }

    @Override
    public boolean getServerGroup(String groupId,String taskId) {
        return managerService.checkTransactionGroup(groupId,taskId);
    }

    @Override
    public boolean getServerGroupState(String groupId) {
        return managerService.checkTransactionGroupState(groupId);
    }
}
