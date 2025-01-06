package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.val;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;//秒杀券

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redisson;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //TODO 创建阻塞队列
    private BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<VoucherOrder>(1024*1024);

    //TODO 创建线程池，用于处理阻塞队列的数据,给一个单线程即可，不需要执行太快
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @Autowired
    private RedissonClient redissonClient;

    private  IVoucherOrderService proxy;


    //TODO 在该类被初始化时，就开始执行阻塞任务
    @PostConstruct
    private void init()
    {
        executorService.submit(new VoucherOrderHandler());
    }

    //TODO 执行阻塞任务，现在是执行消息队列了
    private class VoucherOrderHandler implements Runnable
    {
        String queueName="stream.orders";
        @Override
        public void run()
        {
            while (true)
            {

                try {
                    //获取消息队列中的信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息获取是否成功
                    if(list==null||list.isEmpty())
                    {
                        //失败，说明没消息，继续下一次循环
                        continue;
                    }

                    //获取成功，就下单
                    //解析消息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);//转成voucherOrder
                    System.out.println("voucherOrder"+voucherOrder);
                    handleVoucherOrder(voucherOrder);



                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",entries.getId());



                } catch (Exception e) {

                    log.error("处理订单异常",e);

                    //处理异常
                    handlePendingList();

                    throw new RuntimeException(e);
                }

            }


        }

        private void handlePendingList()
        {
            //和上面的流程会相似
            while (true)
            {

                try {
                    //获取pendinglist中的信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断pendinglist获取是否成功
                    if(list==null||list.isEmpty())
                    {
                        //失败,说明pendinglist没有消息，结束循环
                        break;
                    }

                    //获取pendinglist成功，就下单
                    //解析消息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);//转成voucherOrder
                    handleVoucherOrder(voucherOrder);

                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",entries.getId());



                } catch (Exception e) {//处理pendinglist出现异常

                    log.error("处理订单异常",e);

                    //处理异常,这里不用递归了，直接进入下一个循环，因为这里出现异常，下一次循环还是处理Pendinglist的消息
//                    handlePendingList();

                    throw new RuntimeException(e);
                }

            }

        }
    }

    //TODO 处理订单
    private void handleVoucherOrder(VoucherOrder voucherOrder)
    {
        //获取用户id
        Long userId=voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:"+userId);
        //获取锁
        boolean isLock=lock.tryLock();
        if(!isLock)//获取失败
        {
            log.error("不允许重复下单");
            return;
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }


    //TODO 使用redis消息队列的优化
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId=UserHolder.getUser().getId();
        //创建订单id
        long orderId=redisIdWorker.nextId("order");

        //1.执行lua脚本,在脚本完成消息队列传送，不像下面那个优化，是在外面进行阻塞队列推送
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r=result.intValue();
        if(r!=0)
        {
            //2.1.不为0，代表没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        //TODO 下面这里已经没用了
        //2.2 为0，把下单信息保存到阻塞队列

        //TODO 保存阻塞队列
        //将这些信息放到阻塞队列里面去,异动读入数据库中
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        //塞进阻塞队列中
        voucherOrderQueue.add(voucherOrder);
        //获取代理对象，用在proxy那个地方,复制到成员变量，让异步线程可以获取到
        proxy=(IVoucherOrderService) AopContext.currentProxy();

        //3.返回订单id
        return Result.ok(orderId);


    }


//    @Override TODO 秒杀优化，使用阻塞队列的优化
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户id
//        Long userId=UserHolder.getUser().getId();
//
//        //1.执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        //2.判断结果是否为0
//        int r=result.intValue();
//        if(r!=0)
//        {
//            //2.1.不为0，代表没有购买资格
//            return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
//        //2.2 为0，把下单信息保存到阻塞队列
//        long orderId=redisIdWorker.nextId("order");
//        //TODO 保存阻塞队列
//        //将这些信息放到阻塞队列里面去,异动读入数据库中
//        VoucherOrder voucherOrder=new VoucherOrder();
//        voucherOrder.setUserId(userId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        //塞进阻塞队列中
//        voucherOrderQueue.add(voucherOrder);
//        //获取代理对象，用在proxy那个地方,复制到成员变量，让异步线程可以获取到
//        proxy=(IVoucherOrderService) AopContext.currentProxy();
//
//        //3.返回订单id
//        return Result.ok(orderId);
//
//
//
//    }
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断秒杀是否开始,是否结束
//        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now()))//如果开始时间晚于当前时间
//        {
//            return Result.fail("秒杀尚未开始!!");
//        }
//        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now()))//判断是否已经结束了
//        {
//            return Result.fail("秒杀已经结束！！");
//        }
//
//
//        //3.判断库存是否充足
//        if(seckillVoucher.getStock()<1)
//        {
//            return Result.fail("库存不足");
//        }
//
//        Long id=UserHolder.getUser().getId();
//        //TODO加锁，这个代理不太懂，单体项目才有用,干掉，换redis分布式锁，其实就是用setnx来做
////        synchronized (id.toString().intern()) {//针对同一用户，不同用户进入不影响
////            //获取代理对象（事务），确保下面事务生效
////            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //创建锁对象 使用redisson
////        SimpleRedisLock lock = new SimpleRedisLock("order"+id,stringRedisTemplate);todo这个是自定义的锁
//        RLock lock=redisson.getLock("order"+id);
//
//        //尝试获取锁
//        boolean isLock=lock.tryLock();//默认即可，里面有等待时间（重试），超时释放时间，时间类型（时分秒）
//        //获取失败
//        if(!isLock){
//            return Result.fail("一个人只允许下一单");
//        }
//
//        try {
//            //获取代理对象（事务），确保下面事务生效
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//
//
//
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        //TODO 实现一人一单,下面这种实现的一人一单，当一个用户没有买过，那么这个用户利用n个手机同时下单
        //TODO 就会出现下面得到的count都为0，那就是这个用户在n个手机中都买到了这个优惠券，就会导致用户得到了n张优惠券
            Long id=voucherOrder.getId();

            //查询订单
            LambdaQueryWrapper<VoucherOrder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(VoucherOrder::getUserId, id).eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId());
            Long count=voucherOrderMapper.selectCount(lambdaQueryWrapper);
            if(count>0)
            {
                log.error("用户已经购买过一次了");
                return;
            }

            //4.扣减库存，使用乐观锁的机制 针对不同用户
            // TODO 更新时数据库的stock值和我此时拿到的stock值，是否一致，一致才更新——————————————锁得严一点，能解决问题，但是会并发效率不高 .eq("stock",seckillVoucher.getStock())
            // TODO 将上述条件改为库存大于0即可 针对不同用户
            boolean success=seckillVoucherService.update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id",voucherOrder.getVoucherId())
                    .gt("stock",0)
                    .update();
            if(!success)
            {
                log.error("库存不足！");
            }
            //5.创建订单
            //写入数据库
            voucherOrderMapper.insert(voucherOrder);


    }
}
