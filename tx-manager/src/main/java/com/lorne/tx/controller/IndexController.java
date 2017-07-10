package com.lorne.tx.controller;

import com.lorne.tx.eureka.DiscoveryService;
import com.lorne.tx.model.TxState;
import com.lorne.tx.service.TxService;
import com.netflix.appinfo.InstanceInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by lorne on 2017/7/1.
 */
@Controller
public class IndexController {


    @Autowired
    private TxService txService;

    @Autowired
    private DiscoveryService discoveryService;

    @RequestMapping("/index")
    public String index(HttpServletRequest request) {
        final List<InstanceInfo> configServiceInstances = discoveryService.getConfigServiceInstances();
        TxState state = txService.getState();
        request.setAttribute("info", state);
        return "index";
    }

}
