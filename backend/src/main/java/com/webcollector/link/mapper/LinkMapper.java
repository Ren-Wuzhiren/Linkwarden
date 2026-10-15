package com.webcollector.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.webcollector.link.entity.Link;

/**
 * [软件工程] BaseMapper 已提供 insert / selectById / selectCount / updateById / deleteById。
 * 链接当前只需要这些基础方法，所以先保持空接口。
 *
 * [排错] 该接口没有实现类，由 MyBatis 在启动时用动态代理生成并注入；
 * 但必须落在 @MapperScan("com.webcollector.**.mapper") 的扫描范围内，否则容器里没有 Bean。
 */
public interface LinkMapper extends BaseMapper<Link> {

}
