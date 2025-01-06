package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//TODO 配置redisson客户端
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient()
    {
        //配置类
        Config config = new Config();
        //添加redis地址，这里添加单点的地址，也可以使用config.userClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.48.129:6379").setPassword("123321");
        return Redisson.create(config);//创建客户端
    }
}
