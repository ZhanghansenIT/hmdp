package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService ;

    @Resource
    private CacheClient cacheClient ;
    @Test
    public void testSaveShop() throws InterruptedException {
//        shopService.saveShop2Redis(1L,10L);
//        shopService.saveShop2Redis(2L,10L);
        Shop shop = shopService.getById(1L) ;
        // 设置 商铺1 ，逻辑过期20秒
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L ,shop,20L, TimeUnit.SECONDS);
    }


}
