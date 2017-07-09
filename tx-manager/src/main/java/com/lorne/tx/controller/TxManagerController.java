package com.lorne.tx.controller;

import com.lorne.tx.model.TxServer;
import com.lorne.tx.model.TxState;
import com.lorne.tx.service.TxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

/**
 * Created by lorne on 2017/7/1.
 */
@RestController
@RequestMapping("/tx/manager")
public class TxManagerController {

    @Autowired
    private TxService txService;


    @RequestMapping("/getServer")
    public TxServer getServer() {
        return txService.getServer();
    }


    @RequestMapping("/getServerGroup")
    @ResponseBody
    public boolean getServerGroup(@RequestParam("groupId") String groupId,@RequestParam("taskId") String taskId) {
        return txService.getServerGroup(groupId);
    }

    @RequestMapping("/state")
    public TxState state() {
        return txService.getState();
    }
}
