package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();

        // 2. 从session 中获取对象
        User user = (User) session.getAttribute("user");
        if(user == null) {
            // 3.如果不存在，返回状态码 401 ，未授权
            response.setStatus(401);
            // 4. 拦截
            return false ;
        }
        // 5. 保存信息到 threadLocal
        UserDTO userDTO = new UserDTO() ;
        userDTO.setId(user.getId());
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());

        UserHolder.saveUser(userDTO);
        return true ;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
