package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {


    private final StringRedisTemplate stringRedisTemplate ;
    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key , Object value , Long time , TimeUnit unit) {

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key , Object value , Long time , TimeUnit unit) throws InterruptedException {
        //设置过期时间
        RedisData redisData = new RedisData() ;
//        Thread.sleep(200);  模拟
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R,ID> R queryWithPassThrough(String KeyPrefix , ID id , Class<R> type ,
                                         Function<ID,R> dbFallback,Long time , TimeUnit unit) {
        String key = KeyPrefix + id ;
        // 1. 从 redis 中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key) ;

        // 2. 判断是否存在
        if(StrUtil.isNotBlank(json)) {
            // 3. 存在 直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中是否是 空值
        if(json != null) {
            return null ;
        }

        // 4. 根据id 查询数据库
        R r = dbFallback.apply(id);

        // 5. 不存在，返回错误
        if(r == null) {
            //将空值返回给 redis ,并且设置一个 ttl
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 写入redis

        this.set(key,r,time,unit);
        return r ;

    }
    public <ID ,R> R queryWithLogicalExpire( String keyPrefix , ID id ,Class<R> type ,
                                             Function<ID,R> dbFallback,Long time , TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //如果数据已过期，则尝试获取互斥锁
            log.info("数据已过期，正在尝试重建");
            // 3.存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
//        log.info("现在时间" +LocalDateTime.now());
//        log.info("expireTime" +expireTime);
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            log.info("数据未过期") ;
            return r;
        }
        log.info("数据已过期，正在尝试重建");
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        log.info("lockKey :  " +lockKey);
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            log.info("已获取互斥锁，准备执行重建");
            CACHE_REBUILD_EXECUTOR.submit( ()->{

                try{
                    //再次检查缓存有没有过期，防止在高并发环境下缓存多次重建
                    if(JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), RedisData.class).getExpireTime().isAfter(LocalDateTime.now())){
                        log.info("数据已重建完成，无需再次重建");
                        //数据没过期则直接结束
                        return;
                    }

                    //重建缓存
                    //查询数据库
                    R r1 = dbFallback.apply(id) ;
                    // 写入 redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                    log.info("数据已重建");
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return r;
    }
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS) ;
        return BooleanUtil.isTrue(flag) ;
    }
    private void unLock(String key) {
        stringRedisTemplate.delete(key) ;
    }
    public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        //组装key
        String key = keyPrefix + id;

        //查询缓存信息是否存在
        String value = stringRedisTemplate.opsForValue().get(key);

        //如果命中则直接返回
        if(!StrUtil.isBlank(value)){
            return JSONUtil.toBean(value, type);
        }

        //如果命中的是空值则返回null
        if(value != null){
            return null;
        }

        //执行缓存重建
        String lockKey = "lock:"+type.getSimpleName()+":key"+id;
        R result = null;

        try {
            //先获取互斥锁，互斥锁获取失败的话就休眠一段时间然后重新执行业务逻辑
            if(!tryLock(lockKey)){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }

            //获取互斥锁成功后，再次校验缓存是否存在
            String json = stringRedisTemplate.opsForValue().get(key);
            if(!StrUtil.isBlank(json)){
                //若存在则直接返回
                return JSONUtil.toBean(json,type);
            }

            //执行操作数据库的逻辑
            result = dbFallback.apply(id);

            //模拟重建业务时的延时
            Thread.sleep(200);

            //数据库中也不存在则先将该数据进行缓存，再报错
            if(result == null){
                //缓存空对象，值设置为""，并设置过期时间
                stringRedisTemplate.opsForValue().set(
                        key,
                        "",
                        RedisConstants.CACHE_NULL_TTL,
                        TimeUnit.MINUTES
                );
                return null;
            }

            //将查询出来的数据缓存在redis中
            this.set(key,result,time,unit);

        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            //释放锁的操作需要在finally中执行
            unLock(lockKey);
        }

        //返回查询结果
        return result;
    }






}
