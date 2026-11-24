package com.lawyus.reservation.beauty.technician;

import com.lawyus.reservation.beauty.technician.dto.ShiftCopyRequest;
import com.lawyus.reservation.beauty.technician.dto.ShiftUpdateRequest;
import com.lawyus.reservation.beauty.technician.dto.TechnicianRequest;
import com.lawyus.reservation.beauty.technician.dto.TechnicianShiftsVO;
import com.lawyus.reservation.beauty.technician.dto.TechnicianStatusRequest;
import com.lawyus.reservation.beauty.technician.dto.TechnicianVO;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.OwnerGuard;
import com.lawyus.reservation.common.security.StpStaffUtil;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 技师档案与排班接口：档案维护、排班设置仅店长，排班查询允许技师查看本人。
 */
@RestController
@RequestMapping("/api/v1/b/technicians")
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping
    public Result<List<TechnicianVO>> list() {
        OwnerGuard.check();
        return Result.ok(technicianService.list());
    }

    @PostMapping
    public Result<TechnicianVO> create(@Valid @RequestBody TechnicianRequest request) {
        OwnerGuard.check();
        return Result.ok(technicianService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody TechnicianRequest request) {
        OwnerGuard.check();
        technicianService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable long id, @Valid @RequestBody TechnicianStatusRequest request) {
        OwnerGuard.check();
        technicianService.updateStatus(id, request.status());
        return Result.ok();
    }

    @GetMapping("/{id}/shifts")
    public Result<TechnicianShiftsVO> getShifts(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkSelfOrOwner(id);
        return Result.ok(technicianService.getShifts(id, from, to));
    }

    @PutMapping("/{id}/shifts")
    public Result<Void> updateShifts(@PathVariable long id, @Valid @RequestBody ShiftUpdateRequest request) {
        OwnerGuard.check();
        technicianService.updateShifts(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/shifts/copy")
    public Result<Void> copyShifts(@PathVariable long id, @Valid @RequestBody ShiftCopyRequest request) {
        OwnerGuard.check();
        technicianService.copyShifts(id, request);
        return Result.ok();
    }

    /** 排班查询的受限视图：店长可查任意技师，技师仅可查本人。 */
    private void checkSelfOrOwner(long technicianId) {
        if (StpStaffUtil.isOwner()) {
            return;
        }
        Long selfId = StpStaffUtil.getTechnicianId();
        if (selfId == null || selfId != technicianId) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SCOPE);
        }
    }
}
