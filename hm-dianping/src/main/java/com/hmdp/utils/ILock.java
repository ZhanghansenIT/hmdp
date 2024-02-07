package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec : 超时过期时间
     * @return
     */
    boolean tryLock(long timeoutSec) ;

    void unlock() ;
}
