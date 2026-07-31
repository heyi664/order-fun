package com.heyee.comments.limiter.aop;

import com.heyee.comments.dto.UserDTO;
import com.heyee.comments.limiter.annotation.RateLimiter;
import com.heyee.comments.limiter.exception.RateLimitException;
import com.heyee.comments.utils.UserHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
public class RateLimiterAspect {

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setLocation(new ClassPathResource("limiter.lua"));
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Before("@annotation(rateLimiter)")
    public void before(JoinPoint point, RateLimiter rateLimiter) {
        String key = buildKey(point, rateLimiter);
        Long result = stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimiter.window()),
                String.valueOf(rateLimiter.limit()),
                String.valueOf(System.currentTimeMillis())
        );
        if (result != null && result == 0L) {
            throw new RateLimitException(rateLimiter.message());
        }
    }

    private String buildKey(JoinPoint point, RateLimiter rateLimiter) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        StringBuilder key = new StringBuilder(rateLimiter.key())
                .append(method.getDeclaringClass().getName())
                .append(':')
                .append(method.getName());
        if (rateLimiter.type() == RateLimiter.LimitType.IP) {
            key.append(":ip:").append(clientIp());
        } else if (rateLimiter.type() == RateLimiter.LimitType.USER) {
            UserDTO user = UserHolder.getUser();
            key.append(":user:").append(user == null ? "anonymous" : user.getId());
        }
        return key.toString();
    }

    private String clientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
