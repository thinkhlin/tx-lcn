package com.lorne.tx.controller;

import com.lorne.tx.service.model.TxServer;
import com.lorne.tx.service.model.TxState;
import com.lorne.tx.service.TxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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
        return txService.getServerGroup(groupId,taskId);
    }


    @RequestMapping("/getServerGroupState")
    @ResponseBody
    public boolean getServerGroupState(@RequestParam("groupId") String groupId) {
        return txService.getServerGroupState(groupId);
    }

    @RequestMapping("/sendMsg")
    @ResponseBody
    public boolean sendMsg(@RequestParam("msg") String msg,@RequestParam("model") String model) {
        return txService.sendMsg(model,msg);
    }


    @RequestMapping("/state")
    public TxState state() {
        return txService.getState();
    }
}
