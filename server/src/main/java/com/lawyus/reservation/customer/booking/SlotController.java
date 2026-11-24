package com.lawyus.reservation.customer.booking;

import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.customer.booking.dto.SlotResultVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 时段可用性查询接口，匿名可访问。
 */
@RestController
@RequestMapping("/api/v1/c/slots")
public class SlotController {

    private final SlotQueryService slotQueryService;

    public SlotController(SlotQueryService slotQueryService) {
        this.slotQueryService = slotQueryService;
    }

    @GetMapping
    public Result<SlotResultVO> query(@RequestParam long serviceId,
                                      @RequestParam(required = false) Long technicianId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(slotQueryService.query(serviceId, technicianId, date));
    }
}
