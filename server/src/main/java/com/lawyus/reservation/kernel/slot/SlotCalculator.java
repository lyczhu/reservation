package com.lawyus.reservation.kernel.slot;

import com.lawyus.reservation.common.dto.TimeRange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 时段可用性计算（纯函数）：候选起点按粒度步进，依次判定已过 / 休息 / 已满 / 可约。
 * 缓冲双向生效：新预约与已有预约的结束时间各扩展 buffer 后判重叠，等价于相邻预约间隔不小于 buffer。
 */
public final class SlotCalculator {

    public record BusyRange(LocalTime start, LocalTime end) {
    }

    private SlotCalculator() {
    }

    public static List<Slot> calculate(List<TimeRange> businessHours,
                                       List<TimeRange> shiftIntervals,
                                       List<BusyRange> busyRanges,
                                       int durationMin,
                                       int bufferMinutes,
                                       int granularity,
                                       LocalDate date,
                                       LocalDateTime now,
                                       int minLeadMinutes) {
        if (businessHours == null || businessHours.isEmpty() || durationMin <= 0 || granularity <= 0) {
            return List.of();
        }
        LocalDateTime earliestStart = now.plusMinutes(minLeadMinutes);
        List<Slot> slots = new ArrayList<>();
        for (TimeRange window : businessHours) {
            LocalTime open = LocalTime.parse(window.getStart());
            LocalTime close = LocalTime.parse(window.getEnd());
            if (!open.isBefore(close)) {
                continue;
            }
            for (LocalTime start = open; !start.plusMinutes(durationMin).isAfter(close);
                 start = start.plusMinutes(granularity)) {
                LocalTime end = start.plusMinutes(durationMin);
                SlotState state;
                if (LocalDateTime.of(date, start).isBefore(earliestStart)) {
                    state = SlotState.PAST;
                } else if (!coveredByShift(shiftIntervals, start, end)) {
                    state = SlotState.REST;
                } else if (overlaps(busyRanges, start, end, bufferMinutes)) {
                    state = SlotState.FULL;
                } else {
                    state = SlotState.AVAILABLE;
                }
                slots.add(new Slot(start, end, state));
            }
        }
        return slots;
    }

    /**
     * 「不指定技师」时合并多名技师的结果：任一可约即可约，否则取最差状态提示顾客。
     */
    public static SlotState merge(List<SlotState> states) {
        if (states.contains(SlotState.AVAILABLE)) {
            return SlotState.AVAILABLE;
        }
        if (states.contains(SlotState.FULL)) {
            return SlotState.FULL;
        }
        if (states.contains(SlotState.PAST)) {
            return SlotState.PAST;
        }
        return SlotState.REST;
    }

    public static boolean covers(List<TimeRange> intervals, LocalTime start, LocalTime end) {
        return coveredByShift(intervals, start, end);
    }

    public static boolean conflicts(List<BusyRange> busyRanges, LocalTime start, LocalTime end, int bufferMinutes) {
        return overlaps(busyRanges, start, end, bufferMinutes);
    }

    private static boolean coveredByShift(List<TimeRange> intervals, LocalTime start, LocalTime end) {
        if (intervals == null || intervals.isEmpty()) {
            return false;
        }
        for (TimeRange interval : intervals) {
            LocalTime shiftStart = LocalTime.parse(interval.getStart());
            LocalTime shiftEnd = LocalTime.parse(interval.getEnd());
            if (!start.isBefore(shiftStart) && !end.isAfter(shiftEnd)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(List<BusyRange> busyRanges, LocalTime start, LocalTime end, int bufferMinutes) {
        if (busyRanges == null || busyRanges.isEmpty()) {
            return false;
        }
        LocalTime candidateEnd = end.plusMinutes(bufferMinutes);
        for (BusyRange busy : busyRanges) {
            LocalTime busyEnd = busy.end().plusMinutes(bufferMinutes);
            if (busy.start().isBefore(candidateEnd) && start.isBefore(busyEnd)) {
                return true;
            }
        }
        return false;
    }
}
