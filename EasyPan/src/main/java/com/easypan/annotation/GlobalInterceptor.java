package com.easypan.annotation;

import org.springframework.web.bind.annotation.Mapping;

import java.lang.annotation.*;

/**
 * GlobalInterceptor
 *
 * @author Jia Yaoyi
 * @date 2023/10/13
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Mapping
public @interface GlobalInterceptor {
    /**
     * 检查参数
     * @return
     */
    boolean checkParams() default false;

    /**
     * 检查登陆
     */
    boolean checkLogin() default true;

    boolean checkAdmin() default false;
}
