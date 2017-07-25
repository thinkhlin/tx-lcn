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
import com.lorne.tx.mq.model.NotifyMsg;
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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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


    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(ThreadPoolSizeHelper.getInstance().getInThreadSize());

    public TransactionThreadServiceImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }



    @Override
    public Object secondExecute(final TxTransactionInfo info,Task groupTask,final ProceedingJoinPoint point) throws Throwable {
        logger.info("tx-second-running-start");

        //需要添加事务组，修改事务状态
        String key  = (String) groupTask.getBack().doing();
        Task waitTask  = ConditionUtils.getInstance().getTask(key);

        final String groupId = groupTask.getKey();


        Object obj =  waitTask.execute(new IBack() {
            @Override
            public Object doing(Object... objs) throws Throwable {



                String kid = KidUtils.generateShortUuid();

                TxGroup txGroup = txManagerService.addTransactionGroup(groupId, kid,true);

                //获取不到模块信息重新连接，本次事务异常返回数据.
                if (txGroup == null) {
                    if (!TransactionHandler.net_state) {
                        nettyService.restart();
                    }
                    throw new ServiceException("添加事务组异常.");
                }


                String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), groupId, kid);
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus status = txManager.getTransaction(def);


                try {
                    Object obj = point.proceed();
                    NotifyMsg notifyMsg  = txManagerService.notifyTransactionInfo(groupId, kid, true);

                    if (notifyMsg == null||notifyMsg.getState()==0) {
                        //修改事务组状态异常
                        txManager.rollback(status);
                        compensateService.deleteTransactionInfo(compensateId);
                        throw new ServiceException("修改事务组状态异常.");

                    }
                    txManager.commit(status);
                    return obj;
                }catch (Throwable e){
                    NotifyMsg notifyMsg  = txManagerService.notifyTransactionInfo(groupId, kid, false);
                    if (notifyMsg == null||notifyMsg.getState()==0) {
                        //修改事务组状态异常
                        txManager.rollback(status);
                        compensateService.deleteTransactionInfo(compensateId);
                        throw new ServiceException("修改事务组状态异常.");
                    }
                    txManager.rollback(status);
                    throw e;
                }finally {
                    compensateService.deleteTransactionInfo(compensateId);
                }
            }
        });

        if(obj instanceof  Throwable){
            throw (Throwable) obj;
        }

        logger.info("tx-second-running-end");
        return obj;
    }

    @Override
    public ServiceThreadModel serviceInThread(TxTransactionInfo info, boolean signTask, String _groupId, Task task, ProceedingJoinPoint point) {

        String kid = KidUtils.generateShortUuid();
        TxGroup txGroup = txManagerService.addTransactionGroup(_groupId, kid,false);

        //获取不到模块信息重新连接，本次事务异常返回数据.
        if (txGroup == null) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw new ServiceException("添加事务组异常.");
                }
            });
            task.signalTask();
            if (!TransactionHandler.net_state) {
                nettyService.restart();
            }
            return null;
        }

        //一直获取连接导致数据库连接到最大值️
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = txManager.getTransaction(def);
        Task waitTask = ConditionUtils.getInstance().createTask(kid);
        String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), txGroup.getGroupId(), kid);

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
        NotifyMsg notifyMsg  = txManagerService.notifyTransactionInfo(_groupId, kid, executeOk);

        if (notifyMsg == null||notifyMsg.getState()==0) {
            //修改事务组状态异常
            txManager.rollback(status);
            compensateService.deleteTransactionInfo(compensateId);
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw new ServiceException("修改事务组状态异常.");
                }
            });
            task.signalTask();
            return null;
        }

        ServiceThreadModel model = new ServiceThreadModel();
        model.setStatus(status);
        model.setWaitTask(waitTask);
        model.setTxGroup(txGroup);
        model.setNotifyMsg(notifyMsg);
        model.setCompensateId(compensateId);
        return model;

    }


    //等待线程
    private ScheduledFuture schedule(final ServiceThreadModel model, final String taskId,long time) {
       return  executorService.schedule(new Runnable() {
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
                            if (json == null) {
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
        }, time, TimeUnit.MILLISECONDS);
    }

    @Override
    public void serviceWait(final boolean signTask, final Task task, final ServiceThreadModel model) {
        final Task waitTask = model.getWaitTask();
        final String taskId = waitTask.getKey();
        TransactionStatus status = model.getStatus();

        long st = model.getTxGroup().getStartTime();
        long et =model.getNotifyMsg().getNowTime();

        int tmTime = model.getTxGroup().getWaitTime();

        long time = tmTime*1000 - ((int) (et - st));
        if (time <= 500) {
            //直接返回超时数据
            transactionConfirm(-2, waitTask, status, model, task, signTask);
            return;
        }

        //等待线程
        ScheduledFuture future =  schedule(model, taskId,time);

        if (signTask) {
            task.signalTask();
            logger.info("返回业务数据");
        }else{
            //关闭事务组
            txManagerService.closeTransactionGroup(model.getTxGroup().getGroupId(), waitTask);
        }

        logger.info("进入回滚等待.");
        waitTask.awaitTask();

        //关闭自动回滚业务
        if(!future.isDone()){
            future.cancel(false);
        }

        try {
            int state = (Integer) waitTask.getBack().doing();
            logger.info("单元事务（1：提交 0：回滚 -1：事务模块网络异常回滚 -2：事务模块超时异常回滚）:" + state);
            //事务确认操作
            transactionConfirm(state, waitTask, status, model, task, signTask);
        } catch (Throwable throwable) {
            txManager.rollback(status);
        }
    }


    //事务确认状态
    private void transactionConfirm(int state, Task waitTask, TransactionStatus status, ServiceThreadModel model, Task task, boolean signTask) {
        transactionLock(state, status, model.getCompensateId(), waitTask);

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
        if (state == -100) {
            //定时请求TM资源确认状态
            compensateService.addTask(model.getCompensateId());
        }
    }

    //以下代码必须确保原子性
    private void transactionLock(int state, TransactionStatus status, String compensateId, Task waitTask) {
        try {
            if (state == 1) {
                txManager.commit(status);
            } else {
                txManager.rollback(status);
            }
        }finally {
            compensateService.deleteTransactionInfo(compensateId);
            if (waitTask != null)
                waitTask.remove();
        }
    }
}
