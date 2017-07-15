# LCN分布式事务框架

## 框架特点

1. 支持各种基于spring的db框架
2. 兼容springcloud、dubbo
3. 使用简单，低依赖，代码完全开源
4. 基于切面的强一致性事务框架
5. 高可用，模块可以依赖dubbo或springcloud的集群方式做集群化，TxManager也可以做集群化
6. 支持本地事务和分布式事务共存
7. 事务补偿机制，服务故障或挂机再启动时可恢复事务


## 注意事项

1. 同一个模块单元下的事务是嵌套的。
2. 不同事务模块下的事务是独立的。

备注：框架在多个业务模块下操作更新同一个库下的同一张表下的同一条时，将会出现锁表的情况，会导致分布式事务异常，数据会丧失一致性。

方案：
  希望开发者在设计模块时避免出现多模块更新操作（insert update delete）同一条数据的情况。
  
3. 禁止重名的bean对象。
  事务的补偿机制是基于java反射的方式重新执行一次需要补偿的业务。因此执行的时候需要获取到业务的service对象，LCN是基于spring的ApplicationContent的getBean方法获取bean的对象的。因此不允许出现重名对象。
  
4. 分布式事务的方法下不能开启本地事务

本地事务与分布式事务是兼容的，但是由于分布式事务处理的时候是在本地事务基础上开启了一个线程处理的事务操作。这样做以后其实该业务方法完全可以不支持事务的，分布式事务在线程下处理了事务。在高并发的情况下这样的做法会耗尽链接池资源，因此做如下配置。

也是由于该原因，因此需要在所有参与分布式事务的业务模块上都要添加TxTransaction注解。注解只需要在模块的分布式事务开始方法上添加即可。

例如： 若存在A模块调用B模块的分布式事务配置，若A模块方法调用关系是A下的a1做为开始方法，调用了本地的a2 a3方法。然后在a4远程调用的B模块的b1方法，且B模块的调用关系也是由b1调用了b2 b3。 那么关于分布式事务注解的配置的时候只需要配置A模块的a1方法下，B模块的b1方法下。其他的方法均无须做任何处理。



## 使用示例

分布式事务发起方：
```java

    @Override
    @TxTransaction
    public boolean hello() {
        //本地调用
        testDao.save();
        //远程调用方
        boolean res =  test2Service.test();
        //模拟异常
        int v = 100/0;
        return true;
    }
    
```

分布式事务被调用方(test2Service的业务实现类)
```java

    @Override
    @TxTransaction
    public boolean test() {
        //本地调用
        testDao.save();
        return true;
    }

```

如上代码执行完成以后两个模块都将回滚事务。

说明：需要在分布式事务所有参与方都添加`@TxTransaction`注解。详细见demo教程


## 目录说明

lorne-tx-core 是LCN分布式事务框架的切面核心类库

dubbo-transaction 是LCN dubbo分布式事务框架

springcloud-transaction 是LCN springcloud分布式事务框架

tx-manager 是LCN 分布式事务协调器


## 关于框架的设计原理

见 [TxManager](https://github.com/1991wangliang/tx-lcn/blob/master/tx-manager/README.md)


## demo 说明

demo里包含jdbc\hibernate\mybatis版本的demo

dubbo版本的demo [dubbo-demo](https://github.com/1991wangliang/dubbo-lcn-demo)

springcloud版本的demo [springcloud-demo](https://github.com/1991wangliang/springcloud-lcn-demo)


技术交流群：554855843