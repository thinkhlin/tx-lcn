package com.lorne.tx.service.impl;

import com.lorne.core.framework.exception.ServiceException;
import com.lorne.core.framework.utils.KidUtils;
import com.lorne.core.framework.utils.config.ConfigUtils;
import com.lorne.core.framework.utils.http.HttpUtils;
import com.lorne.core.framework.utils.task.ConditionUtils;
import com.lorne.core.framework.utils.task.IBack;
import com.lorne.core.framework.utils.task.Task;
import com.lorne.tx.bean.TxTransactionInfo;
import com.lorne.tx.bean.TxTransactionLocal;
import com.lorne.tx.mq.model.TxGroup;
import com.lorne.tx.service.TransactionServer;
import com.lorne.tx.service.model.ServiceThreadModel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 分布式事务启动开始时的业务处理
 * Created by lorne on 2017/6/8.
 */
@Service(value = "txRunningTransactionServer")
public class TxRunningTransactionServerImpl extends TxBaseTransactionServerImpl implements TransactionServer {


    private Logger logger = LoggerFactory.getLogger(TxRunningTransactionServerImpl.class);


    private String url;


    public TxRunningTransactionServerImpl() {
        url = ConfigUtils.getString("tx.properties", "url");
    }


    public Object secondExecute(final TxTransactionInfo info, Task groupTask, final ProceedingJoinPoint point) throws Throwable {
        logger.info("tx-second-running-start");

        //需要添加事务组，修改事务状态
        String key = (String) groupTask.getBack().doing();
        Task waitTask = ConditionUtils.getInstance().getTask(key);

        final String groupId = groupTask.getKey();


        Object obj = waitTask.execute(new IBack() {
            @Override
            public Object doing(Object... objs) throws Throwable {


                String kid = KidUtils.generateShortUuid();

                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus status = txManager.getTransaction(def);


                try {


                    String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), groupId, kid);

                    Object obj = point.proceed();

                    TxGroup txGroup = txManagerService.addTransactionGroup(groupId, kid, true);

                    //获取不到模块信息重新连接，本次事务异常返回数据.
                    if (txGroup == null) {
                        txManager.rollback(status);
                        throw new ServiceException("添加事务组异常.");
                    } else {
                        txManager.commit(status);
                    }

                    compensateService.deleteTransactionInfo(compensateId);

                    return obj;
                } catch (Throwable e) {
                    //失败会通知到tx
                    txManager.rollback(status);
                    logger.info("tx-second-running-end");
                    throw e;
                }
            }
        });

        if (obj instanceof Throwable) {
            throw (Throwable) obj;
        }

        logger.info("tx-second-running-end");
        return obj;
    }

    @Override
    public Object execute(final ProceedingJoinPoint point, final TxTransactionInfo info) throws Throwable {


        final String txGroupId = info.getTxGroupId();
        Task groupTask = ConditionUtils.getInstance().getTask(txGroupId);

        //当同一个事务下的业务进入切面时，合并业务执行。
        if (groupTask != null && !groupTask.isNotify()) {
            return secondExecute(info, groupTask, point);
        }

        //分布式事务开始执行
        logger.info("tx-running-start");
        final String taskId = KidUtils.generateShortUuid();
        final Task task = ConditionUtils.getInstance().createTask(taskId);

        threadPool.execute(new Runnable() {
            @Override
            public void run() {

                TxTransactionLocal txTransactionLocal = new TxTransactionLocal();
                txTransactionLocal.setGroupId(txGroupId);
                TxTransactionLocal.setCurrent(txTransactionLocal);


                ServiceThreadModel model = serviceInThread(info, txGroupId, task, point);
                if (model == null) {
                    return;
                }

                Task groupTask = ConditionUtils.getInstance().createTask(txGroupId);
                final String waitTaskKey = model.getWaitTask().getKey();
                groupTask.setBack(new IBack() {
                    @Override
                    public Object doing(Object... objs) throws Throwable {
                        return waitTaskKey;
                    }
                });

                logger.info("taskId-id-tx-running:" + waitTaskKey);
                serviceWait(task, model);

                groupTask.remove();
            }
        });

        task.awaitTask();

        logger.info("tx-running-end");
        //分布式事务执行完毕
        try {
            return task.getBack().doing();
        } finally {
            task.remove();
        }
    }


    public ServiceThreadModel serviceInThread(TxTransactionInfo info, String _groupId, Task task, ProceedingJoinPoint point) {

        String kid = KidUtils.generateShortUuid();

        //一直获取连接导致数据库连接到最大值️
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = txManager.getTransaction(def);
        Task waitTask = ConditionUtils.getInstance().createTask(kid);


        try {

            String compensateId = compensateService.saveTransactionInfo(info.getInvocation(), _groupId, kid);

            final Object res = point.proceed();

            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    return res;
                }
            });
            //通知TxManager调用成功

            TxGroup txGroup = txManagerService.addTransactionGroup(_groupId, kid, false);
            //NotifyMsg notifyMsg = txManagerService.notifyTransactionInfo(_groupId, kid, true);
            if (txGroup == null) {
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
            model.setCompensateId(compensateId);
            return model;


        } catch (final Throwable throwable) {
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objects) throws Throwable {
                    throw throwable;
                }
            });

            //修改事务组状态异常
            txManager.rollback(status);
            task.signalTask();

            return null;
        }
    }


    //等待线程
    private ScheduledFuture schedule(final ServiceThreadModel model, final String waitTaskId, long time) {
        return executorService.schedule(new Runnable() {
            @Override
            public void run() {
                Task task = ConditionUtils.getInstance().getTask(waitTaskId);
                String groupId = model.getTxGroup().getGroupId();
                if (task.getState() == 0) {

                    int hasOk = txManagerService.checkTransactionInfo(groupId, waitTaskId);
                    logger.info("自动超时补偿(socket)->groupId:" + groupId + ",taskId:" + waitTaskId + ",res:" + hasOk);
                    if (hasOk == 1) {
                        task.setBack(new IBack() {
                            @Override
                            public Object doing(Object... objs) throws Throwable {
                                return 1;
                            }
                        });
                        task.signalTask();

                        return;
                    } else {
                        if (hasOk == -1) {
                            // 发起http请求查询状态
                            String json = HttpUtils.get(url + "Group?groupId=" + groupId + "&taskId=" + waitTaskId);
                            logger.info("自动超时补偿(http)->groupId:" + groupId + ",taskId:" + waitTaskId + ",res:" + json);
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

                                task.setBack(new IBack() {
                                    @Override
                                    public Object doing(Object... objs) throws Throwable {
                                        return 1;
                                    }
                                });
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


    public void serviceWait(final Task task, final ServiceThreadModel model) {
        final Task waitTask = model.getWaitTask();
        final String waitTaskId = waitTask.getKey();
        TransactionStatus status = model.getStatus();

        long st = model.getTxGroup().getStartTime();
        long et = model.getTxGroup().getNowTime();

        int tmTime = model.getTxGroup().getWaitTime();

        long time = tmTime * 1000 - ((int) (et - st));
        if (time <= 500) {
            //直接返回超时数据
            task.setBack(new IBack() {
                @Override
                public Object doing(Object... objs) throws Throwable {
                    throw new Throwable("事务模块超时异常.");
                }
            });

            task.signalTask();

            return;
        }


        task.signalTask();
        logger.info("返回业务数据");

        //等待线程
        ScheduledFuture future = schedule(model, waitTaskId, time);


        logger.info("进入回滚等待.");
        waitTask.awaitTask();

        //关闭自动回滚业务
        if (!future.isDone()) {
            future.cancel(false);
        }

        transaction(waitTask,status,model,task);

    }


    //事务确认状态
    public void transactionConfirm(int state, Task waitTask, TransactionStatus status, ServiceThreadModel model, Task task) {
        transactionLock(state, status, model.getCompensateId(), waitTask);

        if (state == -100) {
            //定时请求TM资源确认状态
            compensateService.addTask(model.getCompensateId());
        }

    }

}
