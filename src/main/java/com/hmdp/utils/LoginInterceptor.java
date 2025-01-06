package com.hmdp.utils;

//import com.hmdp.entity.User;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.hash.Hash;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

//TODO 第二个拦截器，判断是否拦截下来
public class LoginInterceptor implements HandlerInterceptor {


    /*
        TODO 进入controller之前
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

       //判断是否拦截，使用threalocal的User判断即可
        if(UserHolder.getUser()==null)
        {
            response.setStatus(401);
            return false;
        }
        //有用户，放行
        return true;
    }

    /*
        TODO 完成之后
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

        // TODO 移除用户
        UserHolder.removeUser();
//        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
