
package com.exemple.transactionservice.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Service;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class UploadRateLimiter {
    
    private final LoadingCache<String, Semaphore> uploadLimiters = 
        CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Semaphore>() {
                @Override
                public Semaphore load(String userId) {
                    return new Semaphore(3);  // 3 uploads simultanés max
                }
            });
    
    public boolean tryAcquire(String userId) {
        return uploadLimiters.getUnchecked(userId).tryAcquire();
    }
    
    public void release(String userId) {
        uploadLimiters.getUnchecked(userId).release();
    }
}