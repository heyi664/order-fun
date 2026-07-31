package com.heyee.comments.limiter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {
    String key() default "rate_limit:";
    int window() default 1;
    int limit() default 2000;
    String message() default "系统繁忙，请稍后再试";
    LimitType type() default LimitType.METHOD;

    enum LimitType {
        IP,
        USER,
        METHOD
    }
}
