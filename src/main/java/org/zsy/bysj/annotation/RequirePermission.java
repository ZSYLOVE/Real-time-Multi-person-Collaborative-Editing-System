package org.zsy.bysj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限验证注解
 * 用于标记需要权限验证的方法或类
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /**
     * 需要的权限类型：READ、WRITE、ADMIN
     */
    String value() default "READ";

}

