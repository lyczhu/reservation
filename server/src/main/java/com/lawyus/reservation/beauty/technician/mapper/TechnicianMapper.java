package com.lawyus.reservation.beauty.technician.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TechnicianMapper extends BaseMapper<Technician> {

    /** 预约提交时锁技师行，串行化同一技师的并发下单，避免间隙锁死锁。 */
    @Select("SELECT id FROM technician WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
