package com.webcollector.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.webcollector.tag.entity.LinkTag;

/**
 * [软件工程] 关联表只需要 BaseMapper 的 insert、delete(Wrapper) 与 selectCount。
 *
 * [排错] 因为 LinkTag 的主键是联合主键，这里刻意不提供任何基于单主键的用法；
 * 调用方一律用 LambdaQueryWrapper 表达条件。
 */
public interface LinkTagMapper extends BaseMapper<LinkTag> {

}
