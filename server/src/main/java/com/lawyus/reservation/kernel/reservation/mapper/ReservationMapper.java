package com.lawyus.reservation.kernel.reservation.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.reservation.kernel.reservation.Reservation;

/**
 * 预约单查询：活跃单按 active_slot=1 过滤，提交事务内以行锁阻断同技师同日并发写入。
 */
public interface ReservationMapper extends BaseMapper<Reservation> {

    /** 服务被预约引用的次数，用于删除保护。 */
    @Select("SELECT COUNT(*) FROM reservation WHERE service_id = #{serviceId}")
    long countByServiceId(@Param("serviceId") Long serviceId);

    /** 指定技师在日期区间内的活跃单数（待确认/待服务），用于停用前校验。 */
    @Select("SELECT COUNT(*) FROM reservation WHERE technician_id = #{technicianId} "
            + "AND status IN (1,2) AND date BETWEEN #{from} AND #{to}")
    long countActiveBetween(@Param("technicianId") Long technicianId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    @Select("SELECT * FROM reservation WHERE store_id = #{storeId} AND date = #{date} AND active_slot = 1")
    List<Reservation> selectActiveByDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    /** 提交事务内锁技师行后，在 READ_COMMITTED 隔离级别下重读当日活跃单。 */
    @Select("SELECT * FROM reservation WHERE technician_id = #{technicianId} AND date = #{date} "
            + "AND active_slot = 1 ORDER BY start_time")
    List<Reservation> selectActiveByTechnicianAndDate(@Param("technicianId") Long technicianId,
                                                     @Param("date") LocalDate date);

    /** 幂等冲突后读取已提交的原单。 */
    @Select("SELECT * FROM reservation WHERE idempotency_key = #{key} FOR UPDATE")
    Reservation selectByIdempotencyKeyForUpdate(@Param("key") String key);

    @Select("SELECT MAX(no) FROM reservation WHERE no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxNoByPrefix(@Param("prefix") String prefix);

    @Select("SELECT COUNT(*) FROM reservation WHERE active_slot = 1 AND verify_code = #{verifyCode}")
    long countActiveByVerifyCode(@Param("verifyCode") String verifyCode);

    @Select("SELECT * FROM reservation WHERE store_id = #{storeId} AND date = #{date}")
    List<Reservation> selectByDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    @Select("SELECT * FROM reservation WHERE verify_code = #{verifyCode} AND status = 2")
    List<Reservation> selectConfirmableByVerifyCode(@Param("verifyCode") String verifyCode);

    /** 手动输码重复核销时按已完成单兑底幂等，取最近完成的一单。 */
    @Select("SELECT * FROM reservation WHERE verify_code = #{verifyCode} AND status = 3 "
            + "ORDER BY completed_at DESC LIMIT 1")
    Reservation selectLatestCompletedByVerifyCode(@Param("verifyCode") String verifyCode);
}
