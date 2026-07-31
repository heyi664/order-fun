package com.heyee.comments.service.cache;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.heyee.comments.config.VoucherCacheProperties;
import com.heyee.comments.entity.Voucher;
import com.heyee.comments.mapper.VoucherMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.heyee.comments.utils.RedisConstants.CACHE_VOUCHER_LIST_KEY;

@Service
public class VoucherListCacheService {

    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private VoucherCacheProperties cacheProperties;

    private LoadingCache<Long, List<Voucher>> voucherListCache;

    @PostConstruct
    public void initCache() {
        voucherListCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .refreshAfterWrite(cacheProperties.getRefreshAfterWrite().toMillis(), TimeUnit.MILLISECONDS)
                .expireAfterWrite(cacheProperties.getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS)
                .build(new CacheLoader<Long, List<Voucher>>() {
                    @Override
                    public List<Voucher> load(Long shopId) {
                        return loadFromRedisThenDb(shopId);
                    }
                });
    }

    public List<Voucher> getVoucherByShopId(Long shopId) {
        switch (cacheProperties.getMode().toLowerCase(Locale.ROOT)) {
            case "mysql":
                return loadFromDb(shopId);
            case "redis":
                return loadFromRedisThenDb(shopId);
            case "caffeine":
            default:
                return voucherListCache.get(shopId);
        }
    }

    public void evictVoucherList(Long shopId) {
        if (shopId == null) {
            return;
        }
        voucherListCache.invalidate(shopId);
        stringRedisTemplate.delete(CACHE_VOUCHER_LIST_KEY + shopId);
    }

    private List<Voucher> loadFromRedisThenDb(Long shopId) {
        String key = CACHE_VOUCHER_LIST_KEY + shopId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toList(json, Voucher.class);
        }
        List<Voucher> vouchers = loadFromDb(shopId);
        if (CollectionUtil.isNotEmpty(vouchers)) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vouchers),
                    cacheProperties.getRedisTtl().toMillis(), TimeUnit.MILLISECONDS);
        }
        return vouchers;
    }

    private List<Voucher> loadFromDb(Long shopId) {
        List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
        return vouchers == null ? Collections.emptyList() : vouchers;
    }
}
