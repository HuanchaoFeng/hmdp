package com.hmdp.utils;


import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {


    /*
        开始时间戳
     */
    private static final long BEGIN_TIMESTAMP=1704067200L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public long nextId(String keyPrefix)
    {
        //生成时间

        LocalDateTime localDateTime = LocalDateTime.now();
        long second_current=localDateTime.toEpochSecond(ZoneOffset.UTC);
        long time=second_current-BEGIN_TIMESTAMP;

        //生成序列号
        //获取当天日期,当天自增长，因为一般一天用不到2^32个订单
        String date=localDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        //再自增长
        //TODO increment方法用于将指定键的值增加1。如果键不存在，它将创建一个新的键，并将值设置为1。如果键已经存在，它将键的值增加1
        long count=stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);

        //拼接并返回，时间戳高位，序列号低32位

        return time << 32 | count;
    }

    //TODO 得到2024.1.1 0点0分的时间戳
//    public static void main(String[] args)
//    {
//        LocalDateTime time = LocalDateTime.of(2024,1,1,0,0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//
//    }

}
