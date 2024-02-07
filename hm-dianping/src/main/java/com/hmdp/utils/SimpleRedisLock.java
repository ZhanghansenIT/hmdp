package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{

    private String name ; // 不同业务有不同的名字
    private StringRedisTemplate stringRedisTemplate ;
    private static final String KEY_PREFIX = "lock:" ;
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) +"-" ;


    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT ;
    static {
        // 初始化
        UNLOCK_SCRIPT = new DefaultRedisScript<>() ;
        // 设置脚本的位置
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取当前线程的唯一标识

        String threadId = ID_PREFIX + Thread.currentThread().getId();

        boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name,threadId,timeoutSec, TimeUnit.SECONDS) ;
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock(){
        // 调用 lua 脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name) ,
                ID_PREFIX + Thread.currentThread().getId()
                ) ;

    }

//    @Override
//    public void unlock() {
//
//        // 获取线程表示
//        String threadId = ID_PREFIX + Thread.currentThread().getId() ;
//        // 获取锁的 id
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name) ;
//        if(threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name) ; // 释放锁
//
//        }
//
//    }
}
