package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;


/*
        TODO 做redis的封装工具类，实现：
        ①：解决缓存穿透
        ②：解决缓存击穿
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private ShopMapper shopMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate, StringRedisTemplate stringRedisTemplate1)
    {
        this.stringRedisTemplate = stringRedisTemplate1;
    }

    //穿透的set
    public void set(String key, Object value, Long time, TimeUnit timeUnit)
    {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //穿透的get
    /*
        TODO keyPrefix是key的前缀，id定义为ID类型，class<R> type是返回数据的类型,
         Function<ID,R> function这个是执行的函数方法，比如要根据id查数据库，就可以直接把方法给我,因为这个工具类不知道你的mapper层是什么
     */
    public <R,ID>R  queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> function,Long time, TimeUnit timeUnit)
    {

        String key=keyPrefix+id;
        //1.根据redis查询缓存
        String Json=stringRedisTemplate.opsForValue().get(key);

        //2.判断存在
        if(StrUtil.isNotBlank(Json))
        {
            //存在，返回数据
//            return JSONUtil.toBean(Json, Shop.class);
            return JSONUtil.toBean(Json, type);

        }
        //该缓存是空值（第四步做的）
        if(Json!=null)//空字符串不是null
        {
            return null;
        }

        //3.不存在——>查数据库
//        Shop shop=shopMapper.selectById(id);
        R r=function.apply(id);
        //4.数据库不存在->return false
        if(r==null)
        {
            //当数据不存在的时候，要将空值写入redis，使用空值法解决redis缓存穿透问题
            stringRedisTemplate.opsForValue().set(key," ",2L, timeUnit);
            return null;
        }
        //5.存在，写入redis，并设置超时时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time, timeUnit);


        //6。返回数据
        return r;
    }


    //击穿的set,使用的是逻辑过期方法
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit)
    {
        //封装到redisData里
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(timeUnit.toSeconds(time))));
        //写入redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //击穿的Get方法，这里只能用于已经缓存的热点数据，默认是缓存了的，没缓存就代表没这个东西，也就用来测试的，平时用上面那个缓存穿透就可以正常进行了，因为不是所有的使用逻辑过期
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> function,Long time, TimeUnit timeUnit)
    {
        String key=keyPrefix+id;
        String Json=stringRedisTemplate.opsForValue().get(key);
//        System.out.println("Json:"+Json);

        //判断是否存在
        if(StrUtil.isBlank(Json))
        {
            return null;
        }

        //命中，把json反序列化为对象
        RedisData redisData=JSONUtil.toBean(Json, RedisData.class);
        R r=JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime=redisData.getExpireTime();
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            //未过期
            return r;
        }
        //已过期，缓存重建
//        System.out.println("过期：");
        //获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=this.tryLock(lockKey);

        //获得锁
        if(isLock)
        {
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    //重建缓存

                    //查数据库
                    R data=function.apply(id);
//                    System.out.println("重建缓存data:");
//                    System.out.println(data);
                    //存入redis
                    setWithLogicalExpire(key,data,time,timeUnit);
                }catch(Exception e){
                    throw new RuntimeException(e);
                }finally {
                    deleteLock(lockKey);
                }
            });
        }

        return r;
    }

    //获得锁
    private boolean tryLock(String key)
    {
        //设置十秒过期，因为一个查询db也就一两秒
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void deleteLock(String key)
    {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds)
    {
        Shop shop=shopMapper.selectById(id);
        //封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }

}
