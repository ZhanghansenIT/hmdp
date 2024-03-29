package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdworker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate ;
    @Resource
    private RedisIdworker redisIdworker ;
    @Resource
    private ISeckillVoucherService seckillVoucherService ;
    @Override
//    @Transactional
    public Result seckillVoucher(Long voucherId) {

        // 1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2。 判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now()) ){
            // 还没开始
            return Result.fail("还没开始") ;
        }

        // 3. 判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束了") ;
        }

        // 4. 判断库存是否充足
        if(voucher.getStock() <1 ) {
            return Result.fail("库存不足") ;
        }
        //用户id
        Long userId = UserHolder.getUser().getId();

//        synchronized (userId.toString().intern()){
            // intern 去常量池中找值相同的对象
        SimpleRedisLock lock = new SimpleRedisLock("order:"+userId ,stringRedisTemplate );
        boolean isLock = lock.tryLock(1200) ;
        if(!isLock) {
            // 获取锁失败
            return Result.fail("不要重复下单") ;
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        Long userId = UserHolder.getUser().getId();
        // 根据用户 id 和优惠券Id ,查询数据库是否有订单了
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 判断是否存在
        if(count > 0) {
            //用户已经购买过了
            return Result.fail("不能重复购买") ;
        }

        // 5. 扣减库存
        // 使用CAS乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1 ")
                .eq("voucher_id",voucherId)
                .gt("stock",0)
                .update() ;


        if(!success) {
            // 扣减失败
            return Result.fail("库存不足 ") ;
        }


        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //订单 id
        long orderId = redisIdworker.nextId("order")  ;
        voucherOrder.setId(orderId) ;

        voucherOrder.setUserId(userId) ;
        //优惠券 id
        voucherOrder.setVoucherId(voucherId) ;

        save(voucherOrder) ;
        return Result.ok(orderId);
    }
}
