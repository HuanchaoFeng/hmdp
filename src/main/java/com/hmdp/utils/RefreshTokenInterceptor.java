package com.hmdp.utils;

//import com.hmdp.entity.User;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

//TODO 第一个拦截器，拦截所有，没有user信息直接放行，让他到第二个拦截器，有用户信息，就刷新，因为如果只有LoginInterceptor的话，只有请求需要拦截的页面时才能刷新user数据，所以就需要设一个第一层拦截器，对所有页面验证，一发现有user信息，直接刷新
public class RefreshTokenInterceptor implements HandlerInterceptor {

    //要在webmvc那里注入
    private StringRedisTemplate stringRedisTemplate;//这里不能直接用@Resouce注入，因为这个类不是component

    //webmvc注入后在这边创建构造类，来初始化
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate)
    {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /*
        TODO 进入controller之前
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //TODO 1.拿到请求头的token
        String token=request.getHeader("authorization");
        if(StrUtil.isBlank(token))//token为空，就是未登录，放行到第二个拦截器
        {
            return true;
        }

        //TODO 2.基于token拿redis中的数据
        Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        if(userMap.isEmpty())//用户不存在,也放行到第二个拦截器
        {

            return true;
        }

        //TODO 4.将查到的hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        //TODO 5.存在就保存至THreadlocal
        UserHolder.saveUser(userDTO);

        //TODO 6.刷新token有效期，因为前面设置了redis保存user的有效期是30分钟，所以要时时更新
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.SECONDS);

        //TODO 7.放行

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
