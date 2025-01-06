package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.testEntity;
import com.hmdp.mapper.testMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/testMapper")
public class testMapperController {

    @Autowired
    private testMapper t;


    @GetMapping("/test")
    public Map queryShopById()
    {
        List<Map> list = t.selectAll();
        Map<String,Integer> map = new HashMap<>();
        for(Map e:list)
        {
            Integer count=(Integer) e.get("count");
            String name = (String) e.get("violate_degree");
            map.put(name,count);
        }
        return map;
    }
}
