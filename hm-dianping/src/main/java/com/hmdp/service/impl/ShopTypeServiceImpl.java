package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 使用 list 类型
        String key = "cache:shopType";

        // 1. 先查询 Redis 中有无数据
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);

        // 2. 若有，将其 List 的集合返回
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            // 3. 将其转换为 JSON 的集合返回
            List<ShopType> list = JSONUtil.toList(shopTypeJsonList.toString(), ShopType.class);
            return Result.ok(list);
        }

        // 4. 若无，从数据库中获取数据
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5. 如果数据库中也没有，直接返回失败
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("数据库和 Redis 中均没有数据");
        }

        // 6. 将数据存储到 Redis 中
        for (ShopType shopType : typeList) {
            stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(shopType));
        }

        // 7. 返回成功
        return Result.ok(typeList);
    }

//    @Override
//    public Result queryTypeList() {
//
//        String key = "cache:shopTye";
//        //1.先查询redis中有无数据
//        String shopType = stringRedisTemplate.opsForValue().get(key);
//
//        //2.若有，那么将其list的集合返回
//        if(StrUtil.isNotBlank(shopType)){
//            //3.将其转换为json的集合返回
//            List<ShopType> list = JSONUtil.toList(shopType, ShopType.class);
//            return  Result.ok(list);
//        }
//
//        // 3. 如果不存在，从数据库中查询，并写入到 reddis中
//
//        List<ShopType> typeList = query().orderByAsc("sort").list();
//        if(typeList==null){
//            //若数据库中也没有，那么直接返回失败
//            return  Result.fail("数据库和redis中均没有");
//        }
//
//        //6.将其存到redis中
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));
//
//        //7.返回成功
//        return Result.ok(typeList);
//
//    }


}
