package com.lawyus.reservation.kernel.reservation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 预约号生成：RSV + yyyyMMdd + 4 位日流水。
 * 流水优先取 Redis 自增；Redis 不可用时按当日最大号回退，唯一索引兜底防重。
 */
@Slf4j
@Component
public class ReservationNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "RSV";
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;
    private final ReservationMapper reservationMapper;

    public ReservationNoGenerator(StringRedisTemplate redisTemplate, ReservationMapper reservationMapper) {
        this.redisTemplate = redisTemplate;
        this.reservationMapper = reservationMapper;
    }

    public String next() {
        String day = LocalDate.now().format(DAY);
        String key = "rsv:no:" + day;
        try {
            Long seq = redisTemplate.opsForValue().increment(key);
            if (seq != null) {
                redisTemplate.expire(key, TTL);
                return format(day, seq);
            }
        } catch (RuntimeException e) {
            log.warn("预约号 Redis 流水不可用，回退数据库计数: {}", e.getMessage());
        }
        String max = reservationMapper.selectMaxNoByPrefix(PREFIX + day);
        long next = max == null ? 1 : Long.parseLong(max.substring(max.length() - 4)) + 1;
        return format(day, next);
    }

    private String format(String day, long seq) {
        return PREFIX + day + String.format("%04d", seq);
    }
}
