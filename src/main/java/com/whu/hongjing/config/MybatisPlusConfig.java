package com.whu.hongjing.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置中心
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册MyBatis-Plus的拦截器插件。
     * 乐观锁功能必须通过注册 OptimisticLockerInnerInterceptor 插件来激活。
     * @return 配置好的拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 1. 创建MyBatis-Plus的拦截器容器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 2. 向容器中添加乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}