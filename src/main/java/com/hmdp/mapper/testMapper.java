package com.hmdp.mapper;


import com.hmdp.entity.testEntity;
import org.apache.ibatis.annotations.Mapper;

import java.security.Key;
import java.util.List;
import java.util.Map;

@Mapper
public interface testMapper {

    List<Map> selectAll();
}
