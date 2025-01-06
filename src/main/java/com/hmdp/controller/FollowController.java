package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;


    //TODO 这个id是博主的id
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow)
    {

        return followService.follow(id,isFollow);

    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id)
    {

        return followService.isFollow(id);

    }

    //实现共同关注,博客用户和浏览用户的交集关注
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id)
    {
        return followService.followCommons(id);
    }
}
