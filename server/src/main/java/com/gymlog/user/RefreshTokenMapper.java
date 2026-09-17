package com.gymlog.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 刷新令牌数据访问接口。
 *
 * <p>注意这张表**没有 {@code deleted} 字段**——它的「删除」是
 * {@code revoked} 标志位，语义比逻辑删除更准确。
 * MyBatis-Plus 对没有该字段的实体不会施加逻辑删除，两者不冲突。
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
