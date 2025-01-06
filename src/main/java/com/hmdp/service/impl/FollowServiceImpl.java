package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    @Override
    public Result follow(Long id, Boolean isFollow) {//这个id是博主的id

        //取浏览用户id
        Long userId= UserHolder.getUser().getId();
        String key="follows:"+userId;//用于存redis的key

        //已经关注了，就取关，未关注就关注

        if(isFollow)
        {
            //新增数据
            Follow follow=new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            Boolean success=save(follow);//保存
            if(success)
            {
                //把关注用户的Id,放到redis的set集合

                stringRedisTemplate.opsForSet().add(key,id.toString());

            }

        }

        else//取关
        {
            boolean success=remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));

            //从redis移除
            if(success)
            {
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {

        System.out.println("id"+id);

        Long userId= UserHolder.getUser().getId();

        //查询是否存在
        Long count=query().eq("user_id",userId).eq("follow_user_id", id).count();


        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {//该id是该博主的userid

        //当前用户id
        Long userId= UserHolder.getUser().getId();
        String key1="follows:"+userId;
        //求交集
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect==null||intersect.isEmpty())
        {
            return Result.ok(Collections.emptyList());
        }
        //解析 id
        List<Long> ids=intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> users = userService.listByIds(ids)
                            .stream()
                            .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                            .collect(Collectors.toList());
        return Result.ok(users);
    }




}
