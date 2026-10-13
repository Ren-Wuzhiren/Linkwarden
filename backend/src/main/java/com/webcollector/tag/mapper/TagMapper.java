package com.webcollector.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.webcollector.tag.entity.Tag;

/**
 * [软件工程] BaseMapper 已提供 insert / selectById / updateById / deleteById 等基础方法。
 * 标签只需要这些，所以这里先保持空接口。
 *
 * [排错] 该接口没有实现类，由 MyBatis 在启动时用动态代理生成并注入；
 * 但必须落在 @MapperScan("com.webcollector.**.mapper") 的扫描范围内，否则容器里没有 Bean。
 */
public interface TagMapper extends BaseMapper<Tag> {

}
