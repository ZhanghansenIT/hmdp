package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate ;
    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 1. 校验手机号码
        if(RegexUtils.isPhoneInvalid(phone)  ){
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机格式错误")  ;
        }
        // 3。 符合就生成验证码
        // 生成6位的验证码
        String code = RandomUtil.randomNumbers(6) ;
        // 4. 保存验证码到 redis ,并设置有效期是 2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

//        session.setAttribute("code",code);

        // 5. 发送验证码
        log.debug("发送短信验证码 :  "+ code);


        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号

        String phone = loginForm.getPhone() ;
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误 !") ;
        }
        // 2. 校验验证码
        // 从 redis中获取验证码
        String Cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone) ;

        String code = loginForm.getCode() ;
        if( Cachecode == null || !Cachecode.equals(code)) {
            // 3. 不一致，报错
            Result.fail("验证码错误") ;
        }
        // 4. 验证码一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 5. 判断用户是否存在
        if(user == null) {
            // 6. 不存在 ，创建新用户
            user = createUserWithPhone(phone) ;
        }
        // 7. 存在，保存信息到 redis
        // 7.1 随机生成 token 作为登录的令牌
        String token = UUID.randomUUID().toString(true) ;

        // 7.2 将User 对象转化为 HashMap 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName ,fieldValue) -> fieldValue.toString()));
        // 7.3 存储
        String tokenKey = LOGIN_USER_KEY+token ;
        stringRedisTemplate.opsForHash().putAll( tokenKey,userMap);
//        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES) ;

        // 8. 返回 token
        return Result.ok(token);
    }
    private User createUserWithPhone(String phone) {
        // 创建新用户

        User user = new User() ;
        user.setPhone(phone) ;
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)) ;
        // 保存
        save(user) ;
        return user;
    }


}
