package com.lawyus.reservation.beauty.reservation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.reservation.dto.ProxyReservationRequest;
import com.lawyus.reservation.beauty.reservation.dto.StaffReasonRequest;
import com.lawyus.reservation.beauty.reservation.dto.StaffRescheduleRequest;
import com.lawyus.reservation.beauty.reservation.dto.StaffReservationDetailVO;
import com.lawyus.reservation.beauty.reservation.dto.StaffReservationListVO;
import com.lawyus.reservation.beauty.reservation.dto.VerifyRequest;
import com.lawyus.reservation.beauty.reservation.dto.VerifyResultVO;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.OwnerGuard;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.customer.booking.BookingService;
import com.lawyus.reservation.customer.booking.dto.ReservationSubmitVO;

import jakarta.validation.Valid;

/**
 * 商家端预约管理接口：列表/详情可双角色（技师仅本人），写操作仅店长，核销双角色。
 */
@RestController
@RequestMapping("/api/v1/b")
public class StaffReservationController {

    private final StaffReservationService staffReservationService;
    private final StaffVerifyService staffVerifyService;

    public StaffReservationController(StaffReservationService staffReservationService,
                                      StaffVerifyService staffVerifyService) {
        this.staffReservationService = staffReservationService;
        this.staffVerifyService = staffVerifyService;
    }

    @GetMapping("/reservations")
    public Result<List<StaffReservationListVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String phone) {
        return Result.ok(staffReservationService.list(from, to, technicianId, status, phone,
                StpStaffUtil.isOwner(), StpStaffUtil.getTechnicianId()));
    }

    @GetMapping("/reservations/{id}")
    public Result<StaffReservationDetailVO> detail(@PathVariable long id) {
        return Result.ok(staffReservationService.detail(id, StpStaffUtil.isOwner(), StpStaffUtil.getTechnicianId()));
    }

    @PostMapping("/reservations")
    public Result<ReservationSubmitVO> createProxy(@Valid @RequestBody ProxyReservationRequest request) {
        OwnerGuard.check();
        BookingService.SubmitResult result = staffReservationService.proxy(request, StpStaffUtil.getStaffId());
        if (result.idempotent()) {
            return new Result<>(ErrorCode.IDEMPOTENT_HIT.getCode(), "重复提交，返回原预约单", result.reservation());
        }
        return Result.ok(result.reservation());
    }

    @PostMapping("/reservations/{id}/confirm")
    public Result<StaffReservationDetailVO> confirm(@PathVariable long id) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.confirm(id, StpStaffUtil.getStaffId()));
    }

    @PostMapping("/reservations/{id}/reject")
    public Result<StaffReservationDetailVO> reject(@PathVariable long id,
                                                   @Valid @RequestBody StaffReasonRequest request) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.reject(id, request.reason(), StpStaffUtil.getStaffId()));
    }

    @PostMapping("/reservations/{id}/cancel")
    public Result<StaffReservationDetailVO> cancel(@PathVariable long id,
                                                   @Valid @RequestBody StaffReasonRequest request) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.cancel(id, request.reason(), StpStaffUtil.getStaffId()));
    }

    @PostMapping("/reservations/{id}/reschedule")
    public Result<StaffReservationDetailVO> reschedule(@PathVariable long id,
                                                       @Valid @RequestBody StaffRescheduleRequest request) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.reschedule(id, request, StpStaffUtil.getStaffId()));
    }

    @PostMapping("/reservations/{id}/no-show")
    public Result<StaffReservationDetailVO> noShow(@PathVariable long id,
                                                   @Valid @RequestBody StaffReasonRequest request) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.noShow(id, request.reason(), StpStaffUtil.getStaffId()));
    }

    @GetMapping("/reservations/{id}/phone")
    public Result<String> phone(@PathVariable long id) {
        OwnerGuard.check();
        return Result.ok(staffReservationService.fullPhone(id, StpStaffUtil.getStaffId()));
    }

    @PostMapping("/verify")
    public Result<VerifyResultVO> verify(@RequestBody VerifyRequest request) {
        return Result.ok(staffVerifyService.verify(StpStaffUtil.getStaffId(), StpStaffUtil.isOwner(),
                StpStaffUtil.getTechnicianId(), request));
    }
}
