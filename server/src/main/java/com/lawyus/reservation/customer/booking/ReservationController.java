package com.lawyus.reservation.customer.booking;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.StpUserUtil;
import com.lawyus.reservation.customer.booking.dto.ReservationDetailVO;
import com.lawyus.reservation.customer.booking.dto.ReservationListVO;
import com.lawyus.reservation.customer.booking.dto.ReservationRescheduleRequest;
import com.lawyus.reservation.customer.booking.dto.ReservationSubmitRequest;
import com.lawyus.reservation.customer.booking.dto.ReservationSubmitVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客预约接口：提交、我的预约列表与详情、取消与改期，均需登录且仅能操作本人预约。
 */
@RestController
@RequestMapping("/api/v1/c/reservations")
public class ReservationController {

    private final BookingService bookingService;
    private final MyReservationService myReservationService;

    public ReservationController(BookingService bookingService, MyReservationService myReservationService) {
        this.bookingService = bookingService;
        this.myReservationService = myReservationService;
    }

    @PostMapping
    public Result<ReservationSubmitVO> submit(@Valid @RequestBody ReservationSubmitRequest request) {
        StpUserUtil.checkLogin();
        BookingService.SubmitResult result = bookingService.submit(StpUserUtil.getUserId(), request);
        if (result.idempotent()) {
            return new Result<>(ErrorCode.IDEMPOTENT_HIT.getCode(), "重复提交，返回原预约单", result.reservation());
        }
        return Result.ok(result.reservation());
    }

    @GetMapping
    public Result<List<ReservationListVO>> list(@RequestParam(required = false) Integer status) {
        StpUserUtil.checkLogin();
        return Result.ok(myReservationService.list(StpUserUtil.getUserId(), status));
    }

    @GetMapping("/{id}")
    public Result<ReservationDetailVO> detail(@PathVariable long id) {
        StpUserUtil.checkLogin();
        return Result.ok(myReservationService.detail(StpUserUtil.getUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<ReservationDetailVO> cancel(@PathVariable long id,
                                              @RequestParam(required = false) String reason) {
        StpUserUtil.checkLogin();
        return Result.ok(myReservationService.cancel(StpUserUtil.getUserId(), id, reason));
    }

    @PostMapping("/{id}/reschedule")
    public Result<ReservationDetailVO> reschedule(@PathVariable long id,
                                                  @Valid @RequestBody ReservationRescheduleRequest request) {
        StpUserUtil.checkLogin();
        return Result.ok(myReservationService.reschedule(StpUserUtil.getUserId(), id, request));
    }
}
