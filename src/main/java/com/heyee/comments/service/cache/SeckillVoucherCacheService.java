package com.heyee.comments.service.cache;

import com.heyee.comments.entity.SeckillVoucher;
import com.heyee.comments.entity.Voucher;
import com.heyee.comments.mapper.VoucherMapper;
import com.heyee.comments.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.heyee.comments.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.heyee.comments.utils.RedisConstants.SECKILL_VOUCHER_KEY;

/**
 * Cache for immutable token-package sale configuration.
 *
 * <p>Stock is deliberately kept in a separate key and is never rebuilt here.
 * Rebuilding stock from MySQL after a cache miss could overwrite Redis stock
 * that has already been reserved by the Lua script but has not yet been
 * persisted by the asynchronous order consumer.</p>
 */
@Slf4j
@Service
public class SeckillVoucherCacheService {

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_BEGIN_AT = "beginAt";
    private static final String FIELD_END_AT = "endAt";
    private static final String FIELD_PER_ORDER_LIMIT = "perOrderLimit";
    private static final String FIELD_PER_USER_LIMIT = "perUserLimit";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /** Returns cached metadata only; this method never accesses MySQL. */
    public Metadata get(Long voucherId) {
        if (voucherId == null) {
            return null;
        }
        Map<Object, Object> fields = stringRedisTemplate.opsForHash()
                .entries(SECKILL_VOUCHER_KEY + voucherId);
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        try {
            return new Metadata(
                    voucherId,
                    integer(fields, FIELD_TYPE),
                    integer(fields, FIELD_STATUS),
                    longValue(fields, FIELD_BEGIN_AT),
                    longValue(fields, FIELD_END_AT),
                    integer(fields, FIELD_PER_ORDER_LIMIT),
                    integer(fields, FIELD_PER_USER_LIMIT));
        } catch (RuntimeException ex) {
            log.warn("Invalid seckill voucher cache metadata, voucherId={}", voucherId, ex);
            return null;
        }
    }

    /** Database fallback for metadata only. It intentionally does not touch the stock key. */
    public Metadata loadMetadataFromDatabase(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            return null;
        }
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null || seckillVoucher.getBeginTime() == null || seckillVoucher.getEndTime() == null) {
            return null;
        }
        return new Metadata(
                voucherId,
                voucher.getType() == null ? 0 : voucher.getType(),
                voucher.getStatus() == null ? 0 : voucher.getStatus(),
                toEpochMillis(seckillVoucher.getBeginTime()),
                toEpochMillis(seckillVoucher.getEndTime()),
                voucher.getPerOrderLimit() == null ? 1 : voucher.getPerOrderLimit(),
                voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit());
    }

    /** Stores only metadata and gives it the same lifecycle as the sale. */
    public void cacheMetadata(Metadata metadata) {
        if (metadata == null || metadata.getEndAt() <= System.currentTimeMillis()) {
            return;
        }
        String key = SECKILL_VOUCHER_KEY + metadata.getVoucherId();
        stringRedisTemplate.opsForHash().putAll(key, metadata.toHash());
        stringRedisTemplate.expireAt(key, new Date(metadata.getEndAt()));
    }

    /**
     * Called after a successful publish transaction. This is the only method
     * that initializes the Redis stock key.
     */
    public void initializePublishedVoucher(Voucher voucher) {
        if (voucher == null || voucher.getId() == null || voucher.getStock() == null
                || voucher.getBeginTime() == null || voucher.getEndTime() == null) {
            throw new IllegalArgumentException("Published token package data is incomplete");
        }
        Metadata metadata = new Metadata(
                voucher.getId(),
                voucher.getType() == null ? 0 : voucher.getType(),
                voucher.getStatus() == null ? 0 : voucher.getStatus(),
                toEpochMillis(voucher.getBeginTime()),
                toEpochMillis(voucher.getEndTime()),
                voucher.getPerOrderLimit() == null ? 1 : voucher.getPerOrderLimit(),
                voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit());
        if (metadata.getEndAt() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Published token package has already ended");
        }
        cacheMetadata(metadata);
        String stockKey = SECKILL_STOCK_KEY + voucher.getId();
        stringRedisTemplate.opsForValue().set(stockKey, voucher.getStock().toString());
        stringRedisTemplate.expireAt(stockKey, new Date(metadata.getEndAt()));
    }

    private static int integer(Map<Object, Object> fields, String key) {
        return Integer.parseInt(value(fields, key));
    }

    private static long longValue(Map<Object, Object> fields, String key) {
        return Long.parseLong(value(fields, key));
    }

    private static String value(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing cache field: " + key);
        }
        return value.toString();
    }

    private static long toEpochMillis(java.time.LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static final class Metadata {
        private final Long voucherId;
        private final int type;
        private final int status;
        private final long beginAt;
        private final long endAt;
        private final int perOrderLimit;
        private final int perUserLimit;

        public Metadata(Long voucherId, int type, int status, long beginAt, long endAt,
                        int perOrderLimit, int perUserLimit) {
            this.voucherId = voucherId;
            this.type = type;
            this.status = status;
            this.beginAt = beginAt;
            this.endAt = endAt;
            this.perOrderLimit = perOrderLimit;
            this.perUserLimit = perUserLimit;
        }

        public Map<String, String> toHash() {
            Map<String, String> fields = new HashMap<>();
            fields.put(FIELD_TYPE, String.valueOf(type));
            fields.put(FIELD_STATUS, String.valueOf(status));
            fields.put(FIELD_BEGIN_AT, String.valueOf(beginAt));
            fields.put(FIELD_END_AT, String.valueOf(endAt));
            fields.put(FIELD_PER_ORDER_LIMIT, String.valueOf(perOrderLimit));
            fields.put(FIELD_PER_USER_LIMIT, String.valueOf(perUserLimit));
            return fields;
        }

        public Long getVoucherId() { return voucherId; }
        public int getType() { return type; }
        public int getStatus() { return status; }
        public long getBeginAt() { return beginAt; }
        public long getEndAt() { return endAt; }
        public int getPerOrderLimit() { return perOrderLimit; }
        public int getPerUserLimit() { return perUserLimit; }
    }
}
