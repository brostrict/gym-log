package com.gymlog.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联数据访问接口。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
