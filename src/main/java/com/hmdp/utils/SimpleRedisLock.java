package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements  ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String ID_prefix= UUID.randomUUID().toString()+"-";//使用UUID生成线程ID的前缀，因为单单使用thread的id的话，会出现集群下的每个服务都会管理自己的thread，有可能会重复

    //使用构造函数负值
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate)
    {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        //获取线程标识
        String threadId=ID_prefix+Thread.currentThread().getId();
        //获取锁,利用setnx来实现
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent("lock:"+name,threadId,timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//做一个比较，Boolean可能为空(拆箱的风险)，这个函数调用之后会把null和false都记为false
    }

    @Override
    public void unlock() {
        //TODO这里还有一个问题，就是判断一致之后，如果阻塞了，就会出现问题，还是有误删的风险（看笔记）
        //TODO 所以这里使用lua脚本实现下面的所有语句的，实现Lua脚步可以保证所有语句的原子性（没实现）
        //获取线程标识
        String threadId=ID_prefix+Thread.currentThread().getId();
        //获取锁中的标识
        String id=stringRedisTemplate.opsForValue().get("lock:"+name);
        //判断是否一致
        if(threadId.equals(id))//一致才删
        {
            stringRedisTemplate.delete("lock:"+name);
        }

    }




}
