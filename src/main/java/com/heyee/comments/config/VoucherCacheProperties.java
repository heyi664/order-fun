package com.heyee.comments.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "heyee.cache.voucher-list")
public class VoucherCacheProperties {
    private String mode = "caffeine";
    private Duration refreshAfterWrite = Duration.ofSeconds(5);
    private Duration expireAfterWrite = Duration.ofMinutes(10);
    private Duration redisTtl = Duration.ofMinutes(30);
}
