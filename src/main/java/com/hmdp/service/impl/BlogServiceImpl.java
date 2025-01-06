package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
//import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();

        // 查询用户
        records.forEach(blog ->{
            this.insertBlog(blog);
            this.isBlogLiked(blog);
        });

        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询Blog
        Blog blog = baseMapper.selectById(id);

        if(blog==null){return Result.fail("笔记不存在");}

        //查询该篇Blog的用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
//        insertBlog(blog);  TODO 这里能够调用这个，其实是因为Blog是对象，对象是通过引用传递的

        //查询该篇blog是否被用户点赞 TODO userId2是当前浏览的浏览用户
        Long userId2=UserHolder.getUser().getId();
        String key="blog:liked:"+id;//redis中的键
        Boolean isliked=stringRedisTemplate.opsForSet().isMember(key,userId2.toString());
        //插入值，传数据给前端展示即可,islike不是数据库字段
        blog.setIsLike(BooleanUtil.isTrue(isliked));
        return Result.ok(blog);

    }

    public void insertBlog(Blog blog)
    {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    public void isBlogLiked(Blog blog)
    {
        UserDTO userDTO=UserHolder.getUser();
        if(userDTO==null)return;//未登录直接返回，因为这是只用在首页展示
        Long userId=UserHolder.getUser().getId();
        String key="blog:liked:"+blog.getId();
        Boolean isliked=stringRedisTemplate.opsForSet().isMember(key,userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isliked));
    }

    /*
        是否点赞
     */
    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long userId= UserHolder.getUser().getId();

        //判断当前登录用户是否已经点赞
        String key="blog:liked:"+id;//这个id是博客id
        Boolean islike=stringRedisTemplate.opsForSet().isMember(key,userId.toString());

        //未点赞可以点赞，加到redis的set集合中（点赞用户）
        if(BooleanUtil.isFalse(islike))
        {
            //更新数据库
            boolean success=update().setSql("liked=liked+1").eq("id",id).update();
            //更新redis
            if(success)
            {
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }
        else//已点赞就取消点赞，两个同时取消
        {
            //更新数据库
            boolean success=update().setSql("liked=liked-1").eq("id",id).update();
            if(success)
            {
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }


}
