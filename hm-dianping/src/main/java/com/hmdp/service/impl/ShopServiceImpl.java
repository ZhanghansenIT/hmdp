package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
    private StringRedisTemplate stringRedisTemplate ;
    @Resource
    private CacheClient cacheClient ;
    @Override
    public Result queryByid(Long id) {
        // 解决缓存穿透
//        Shop shop = queryWithPassThrough(id) ;
//       Shop shop =  cacheClient
//               .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES) ;
        // 解决缓存击穿
//        Shop shop = queryWithPassMutex(id) ;
        // 利用逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id) ;
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS); ;


        if(shop == null) {
            return Result.fail("店铺不存在") ;
        }
        return Result.ok(shop);
    }

    /**
     * 解决缓存击穿, 热点key 失效
     * @param id
     * @return
     */
//    public Shop queryWithPassThrough(Long id ) {
//        String key = CACHE_SHOP_KEY + id ;
//        // 1. 从 redis 中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key) ;
//        // 解决缓存穿透
//
//        // 2. 判断是否存在
//        if(StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在 直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 这里就是 如果是空字符串
//        if(shopJson !=null) {
//            return null ;
//        }
//        // 后面就是不存在的逻辑了
//        // 4. 根据id 查询数据库
//        Shop shop = getById(id) ;
//
//        // 5. 不存在，返回错误
//
//        if(shop == null) {
//            //将空值返回给 redis ,并且设置一个 ttl
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//
//    }
//    public Shop queryWithPassMutex(Long id ) {
//
//        // 使用互斥锁来解决缓存击穿问题
//
//
//        String key = CACHE_SHOP_KEY + id ;
//        // 1. 从 redis 中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key) ;
//
//        // 2. 判断是否存在
//        if(StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在 直接返回
//
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 判断是否是null 如果是null,则数据库中也不存咋
//        if(shopJson !=null) {
//            return null ;
//        }
//
//        // 4. 实现缓存重建
//        // 4.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断是否获取成功
////            Thread.sleep(200);
//            // 4.3 失败，则休眠并重试
//            if(!isLock) {
//                Thread.sleep(50) ;
//                return queryWithPassMutex(id) ;
//            }
//
//            // 4.4 成功，根据id 查询数据库
//
//            shop = getById(id);
//            // 5. 不存在，返回错误
//
//            // 数据库中也不存在，则将空返回给 redis
//            if(shop == null) {
//                //将空值返回给 redis ,并且设置一个 ttl
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            unLock(lockKey);
//        }
//        return shop;
//    }

    /**
     * 逻辑过期，封装逻辑失效
     * @param id
     * @param expireSeconds
     */
//    public void saveShop2Redis(Long id , Long expireSeconds ) throws InterruptedException {
//        // 查询店铺的信息
//        Shop shop = getById(id) ;
//        Thread.sleep(200);
//
//        // 封装逻辑过期时间
//        RedisData redisData = new RedisData() ;
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入到 redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id ,JSONUtil.toJsonStr(redisData));
//
//    }
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire( Long id ) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis查询商铺缓存
//        String json = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isBlank(json)) {
//            // 3.存在，直接返回
//            return null;
//        }
//        // 4.命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1.未过期，直接返回店铺信息
//            return shop;
//        }
//        // 5.2.已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 6.2.判断是否获取锁成功
//        if (isLock){
//            CACHE_REBUILD_EXECUTOR.submit( ()->{
//
//                try{
//                    //重建缓存
//                    this.saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//        // 6.4.返回过期的商铺信息
//        return shop;
//    }
//
    /**
     * 互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS) ;
        return BooleanUtil.isTrue(flag) ;
    }
    private void unLock(String key) {
       stringRedisTemplate.delete(key) ;
    }


    /**
     * 先更新数据库再删除缓存 -- 在满足原子性的情况下，安全问题概率低
     * 对于单体系统，利用事务机制来保证原子性 
     * @param shop
     * @return
     */

    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId() ;
        if(id == null) {
            return Result.fail("店铺Id不能为空") ;
        }

        // 1. 先操作数据库
        updateById(shop) ;

        // 2. 删除缓存
        String key = CACHE_SHOP_KEY + id ;
        stringRedisTemplate.delete(key) ;
        return Result.ok();
    }


}
