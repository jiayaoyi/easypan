package com.easypan.annotation;

/**
 * VerifyParam
 *
 * @author Jia Yaoyi
 * @date 2023/10/13
 */

import com.easypan.entity.enums.VerifyRegexEnum;
import org.apache.ibatis.plugin.Intercepts;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD,ElementType.PARAMETER})
public @interface VerifyParam {

    int min() default -1;

    int max() default -1;

    boolean required() default false;

    VerifyRegexEnum regex() default VerifyRegexEnum.NO;
}
