package com.lawyus.reservation.common.validation;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 时间区间校验：HH:mm 格式且 start < end，不合法统一抛参数错误。
 */
public final class TimeRangeValidator {

    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");

    private TimeRangeValidator() {
    }

    public static void validate(List<TimeRange> ranges) {
        for (TimeRange range : ranges) {
            if (!isValidTime(range.getStart()) || !isValidTime(range.getEnd())
                    || range.getStart().compareTo(range.getEnd()) >= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "时间段不合法");
            }
        }
    }

    private static boolean isValidTime(String value) {
        return value != null && TIME_PATTERN.matcher(value).matches();
    }
}
