package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.compensate.service.CompensateService;
import com.lorne.tx.mq.handler.TransactionHandler;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.mq.service.MQTxManagerService;
import com.lorne.tx.mq.service.NettyService;
import com.lorne.tx.service.TransactionThreadService;
import com.lorne.tx.service.model.ServiceThreadModel;
import com.lorne.tx.utils.ThreadPoolSizeHelper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by lorne on 2017/6/9.
 */
@Service
public class TransactionThreadServiceImpl implements TransactionThreadService {

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private MQTxManagerService txManagerService;

    @Autowired
    private NettyService nettyService;

    @Autowired
    private CompensateService compensateService;

    private Logger logger = LoggerFactory.getLogger(TransactionThreadServiceImpl.class);

    private String url;

    private Executor  threadPool = Executors.newFixedThreadPool(ThreadPoolSizeHelper.getInstance().getInThreadSize());

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(ThreadPoolSizeHelper.getInstance().getInThreadSize());

    public TransactionThreadServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }

    @Override
    public ServiceThreadModel serviceInThread(TxTransactionInfo info, boolean signTask, String _groupId, Task task, ProceedingJoinPoint point) {

        String kid = KidUtils.generateShortUuid();
        TxGroup txGroup = txManagerService.addTransactionGroup(_groupId, kid);

        //获取不到模块信息重新连接，本次事务异常返回数据.
        if (txGroup == null) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw new ServiceException("添加事务组异常.");
                }
            });
            task.signalTask();
            if(!TransactionHandler.net_state) {
                nettyService.restart();
            }
            return null;
        }

        String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), txGroup.getGroupId(), kid);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = txManager.getTransaction(def);
        Task waitTask = ConditionUtils.getInstance().createTask(kid);
        //发送数据是否成功
        boolean isSend = false;

        //执行是否成功
        boolean executeOk = false;

        try {
            final Object res = point.proceed();
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    return res;
                }
            });
            //通知TxManager调用成功
            executeOk = true;

        } catch (final Throwable throwable) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw throwable;
                }
            });
            //通知TxManager调用失败
            executeOk = false;
        }
        isSend = txManagerService.notifyTransactionInfo(_groupId, kid, executeOk);
        ServiceThreadModel model = new ServiceThreadModel();
        model.setStatus(status);
        model.setWaitTask(waitTask);
        model.setTxGroup(txGroup);
        model.setNotifyOk(isSend);
        model.setCompensateId(compensateId);
        return model;

    }


    private void waitSignTask(Task task,boolean signTask, boolean isNotifyOk) {
        if (isNotifyOk == false) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw new ServiceException("修改事务组状态异常.");
                }
            });
        }
        if(signTask) {
            task.signalTask();
            logger.info("返回业务数据");
        }
    }

    @Override
    public void serviceWait(final boolean signTask, final Task task, final ServiceThreadModel model) {
        final Task waitTask = model.getWaitTask();
        final String taskId = waitTask.getKey();
        TransactionStatus status = model.getStatus();


        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                Task task = ConditionUtils.getInstance().getTask(taskId);
                String groupId = model.getTxGroup().getGroupId();
                if (task.getState() == 0) {

                    int hasOk = txManagerService.checkTransactionInfo(groupId, taskId);
                    if (hasOk == 1) {
                        task.setState(1);
                        task.signalTask();
                    } else {
                        if (hasOk == -1) {
                            // 发起http请求查询状态
                            String json = HttpUtils.get(url + "Group?groupId=" + groupId + "&taskId=" + taskId);
                            if(json==null){
                                //请求tm访问失败
                                task.setBack(new IBack() {
                                    @Override
                                    public Object doing(Object... objects) throws Throwable {
                                        return -100;//自动回滚补偿时也没有访问到tm
                                    }
                                });

                                task.signalTask();
                                return;

                            }
                            if (json.contains("true")) {

                                task.setState(1);
                                task.signalTask();

                                return;
                            }
                        }
                        task.setBack(new IBack() {
                            @Override
                            public Object doing(Object... objects) throws Throwable {
                                return -2;
                            }
                        });
                        logger.info("自定回滚执行");
                        task.signalTask();
                    }
                }
            }
        }, model.getTxGroup().getWaitTime(), TimeUnit.SECONDS);


        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                waitSignTask(task,signTask, model.isNotifyOk()); //执行顺序 2
            }
        };
        threadPool.execute(runnable);

        if (!signTask) {
            txManagerService.closeTransactionGroup(model.getTxGroup().getGroupId(), waitTask); //执行顺序 3
        }
        logger.info("进入回滚等待.");
        waitTask.awaitTask();

        try {
            int state = (Integer) waitTask.getBack().doing();
            logger.info("单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + state);

            transactionLock(state,status,model.getCompensateId(),waitTask);

            if (state == -1) {
                task.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        throw new Throwable("事务模块网络异常.");
                    }
                });
            }
            if (state == -2) {
                task.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        throw new Throwable("事务模块超时异常.");
                    }
                });
            }

            //主程序的业务数据返回
            if (!signTask) {
                task.signalTask();
            }
            if(state==-100) {
                //定时请求TM资源确认状态
                compensateService.addTask(model.getCompensateId());
            }

        } catch (Throwable throwable) {
            txManager.rollback(status);
        }
    }

    private synchronized void transactionLock(int state, TransactionStatus status, String compensateId, Task waitTask) {
        if (state == 1) {
            txManager.commit(status);
            compensateService.deleteTransactionInfo(compensateId);
            waitTask.remove();
        } else {
            txManager.rollback(status);
            compensateService.deleteTransactionInfo(compensateId);
            waitTask.remove();
        }
    }

}
