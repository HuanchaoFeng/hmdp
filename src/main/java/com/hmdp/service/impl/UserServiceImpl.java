package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.spel.ast.NullLiteral;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;

//    TODO 实现短信发送
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号,正则化校验
        if(RegexUtils.isPhoneInvalid(phone))
        {
            //不符合，返回错误
            return Result.fail("手机号格式错误");
        }

        //生成验证码
        String phoneCode= RandomUtil.randomNumbers(6);

        //保存验证码到session
        //session.setAttribute("phoneCode",phoneCode);
        //使用redis共享session，使用login:code:+phone作为key，这样可以多个服务器访问时，不同用户验证的时候可以找到自己的验证码
        //  TODO LOGIN_CODE_KEY,LOGIN_CODE_TTL只是一个封装好的final常量，在RedisConstants里面
        //  TODO 保存验证码到redis中,并设置2分钟有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone, phoneCode,LOGIN_CODE_TTL, TimeUnit.MINUTES);


        //发送验证码(模拟）
        log.debug("发送验证码成功，验证码: {}",phoneCode);

        //返回Ok
        return Result.ok();
    }

//    TODO 实现登录功能,登录和注册是一起的
    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(loginFormDTO.getPhone()))
        {
            //不符合，返回错误
            return Result.fail("手机号格式错误");
        }

        //2.校验验证码
//        String sessionPhoneCode=(String)session.getAttribute("phoneCode");//session存的验证码
//        String userPhoneCode= loginFormDTO.getCode();
//        if(sessionPhoneCode== null || !sessionPhoneCode.equals(userPhoneCode))
//        {
//            return Result.fail("验证码错误");
//        }
        //TODO 2.从redis获取验证码
        String cacheCode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginFormDTO.getPhone());
        String code=loginFormDTO.getCode();//用户输入的验证码
        if(cacheCode==null || !cacheCode.equals(code))
        {
            return Result.fail("验证码错误");
        }


        //3.根据手机号查用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone,loginFormDTO.getPhone());
        User user=userMapper.selectOne(queryWrapper);

        //4.不存在,创建完跳第6步
        if(user==null)
        {
            //创建用户
            user=createUserByPhone(loginFormDTO.getPhone());
        }
        //5.存在跳到第6步

        //6.保存用户信息,将user专成UserDTO,以此将一些敏感信息去掉
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //TODO 6.保存用户信息到redis中
        //6.1随机生成token，作为登录令牌
        String token= UUID.randomUUID().toString(true);
        //6.2将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        //除了转为hash，还得把integer类型的转换成其他，因为stringRedisTemplate只能是string型
        Map<String,Object> userDTOHash=BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userDTOHash);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.SECONDS);//有效期

        //TODO 把token返回
        return Result.ok(token);
    }

    //TODO 创建用户
    private User createUserByPhone(String phone)
    {
        //创建用户
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        //保存用户信息到DB
        userMapper.insert(user);

        return user;
    }
}
