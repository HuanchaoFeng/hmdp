package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import jakarta.annotation.Resource;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopMapper shopMapper;

    @Resource
    private CacheClient cacheClient;

    /*
        TODO 这个查询，解决了缓存穿透（使用空缓存值）和缓存击穿问题（互斥/逻辑过期）
            互斥锁，redis中提供的setnx:set the value of a key, only if the key does not exist

        TODO 缓存穿透queryWithPassThrough 缓存击穿queryWithMutex
     */

    //总体queryByID,调用穿透和击穿
    public Result queryByID(Long id)
    {
        //穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,id2 -> shopMapper.selectById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,id2 -> shopMapper.selectById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        System.out.println("shop:"+shop);
        if(shop==null)
        {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }


    //TODO 解决缓存穿透
    public Shop queryWithPassThrough(Long id)
    {
        String key="cache:shop:"+id;
        //1.根据redis查询缓存
        String shopJson=stringRedisTemplate.opsForValue().get(key);

        //2.判断存在
        if(StrUtil.isNotBlank(shopJson))
        {
            //存在，返回数据
            Shop shop= JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //该缓存是空值（第四步做的）
        if(shopJson!=null)//空字符串不是null
        {
            return null;
        }

        //3.不存在——>查数据库
        Shop shop=shopMapper.selectById(id);
        //4.数据库不存在->return false
        if(shop==null)
        {
            //当数据不存在的时候，要将空值写入redis，使用空值法解决redis缓存穿透问题
            stringRedisTemplate.opsForValue().set(key," ",2L, TimeUnit.MINUTES);
            return shop;
        }
        //5.存在，写入redis，并设置超时时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);


        //6。返回数据
        return shop;
    }

    //TODO 缓存击穿
    private Shop queryWithMutex(Long id)
    {
        String key="cache:shop:"+id;
        //1.根据redis查询缓存
        String shopJson=stringRedisTemplate.opsForValue().get(key);

        //2.判断存在
        if(StrUtil.isNotBlank(shopJson))
        {
            //存在，返回数据
            Shop shop= JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //该缓存是空值（第四步做的）
        if(shopJson!=null)//空字符串不是null
        {
            return null;
        }

        //TODO 缓存重建

        //获取互斥锁
        String lockKey="lock:shop"+id;
        Shop shop=null;
        try {
            boolean isLock=tryLock(lockKey);
            //是否成功
            //失败，则休眠
            if(!isLock)
            {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //获取锁成功
            //3.不存在——>查数据库
            shop = shopMapper.selectById(id);
            //模拟重建过程的并发操作，放一个延时
            Thread.sleep(200);
            //4.数据库不存在->return false
            if(shop==null)
            {
                //当数据不存在的时候，要将空值写入redis，使用空值法解决redis缓存穿透问题
                stringRedisTemplate.opsForValue().set(key," ",2L, TimeUnit.MINUTES);
                return shop;
            }
            //5.存在，写入redis，并设置超时时间
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {

            //释放互斥锁,都得释放
            deleteLock(lockKey);
        }




        //6。返回数据
        return shop;

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






//    做出一个事务，一个错即全退
    @Transactional
    public Result updateByID(Shop shop)
    {
        //策略，先更新数据库，再删缓存，是双写一致性的较为好的办法

        if(shop.getId()==null)
        {
            return Result.fail("id为空");
        }

        //更新数据库
        shopMapper.updateById(shop);

        //删除缓存
        stringRedisTemplate.delete("cache:shop:"+shop.getId());


        return Result.ok();
    }
}
