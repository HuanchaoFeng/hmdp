package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    //这行代码声明了一个ExecutorService对象es，它是一个固定大小为500的线程池,这意味着线程池中最多可以同时运行500个线程
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        //创建了一个 CountDownLatch 实例，并且初始化计数为 300。这意味着在计数达到 0 之前，任何在 countDownLatch 上等待的线程都将被阻塞
        //以确保主线程（运行测试的线程）在所有 300 个子线程完成它们的任务之前不会提前终止
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable runnable = ()->{

            for(int i=0;i<100;i++)
            {
                long id= redisIdWorker.nextId("order");
                System.out.println("id:"+id);
            }
            // CountDownLatch 的计数减 1
            countDownLatch.countDown();

        };

        long begin = System.currentTimeMillis();
//        将它们提交给ExecutorService执行。这意味着将有300个线程并发执行runnable中的代码
        for(int i=0;i<300;i++)es.submit(runnable);
        // 等待所有子线程完成
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time="+(end-begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    }


}
