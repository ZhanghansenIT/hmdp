package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate ;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
//        stringRedisTemplate.opsForValue().set("token",token);
        if(StrUtil.isBlank(token)) {

            return true ;
        }
        String key =  RedisConstants.LOGIN_USER_KEY+token ;
        // 2. 基于Token 获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(key);

//        UserDTO user = (UserDTO) session.getAttribute("user");
        if(userMap.isEmpty()) {

            return true ;
        }
        // 5. 将查询到的 hash数据转换成 UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6. 保存信息到 threadLocal
        UserHolder.saveUser(userDTO);
        // 7. 刷新有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES);
        return true ;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
