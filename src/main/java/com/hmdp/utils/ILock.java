package com.hmdp.utils;

public interface ILock {

    /*
        获取锁
     */

    boolean tryLock(long timeoutSeconds);

    /*
        释放锁
     */
    void unlock();
}
