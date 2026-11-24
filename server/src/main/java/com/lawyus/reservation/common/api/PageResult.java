package com.lawyus.reservation.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应体，字段与前端分页契约一致，可由 MyBatis-Plus 分页结果转换。
 */
public record PageResult<T>(List<T> records, long total, long current, long size) {

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}