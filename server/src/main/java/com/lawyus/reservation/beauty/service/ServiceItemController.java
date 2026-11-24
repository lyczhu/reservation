package com.lawyus.reservation.beauty.service;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.service.dto.ServiceItemRequest;
import com.lawyus.reservation.beauty.service.dto.ServiceItemVO;
import com.lawyus.reservation.beauty.service.dto.ServiceStatusRequest;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.dto.SortRequest;
import com.lawyus.reservation.common.security.OwnerGuard;

import jakarta.validation.Valid;

/**
 * 服务项目的增删改查、上下架与排序接口，仅店长可访问。
 */
@RestController
@RequestMapping("/api/v1/b/services")
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    public ServiceItemController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping
    public Result<List<ServiceItemVO>> list() {
        OwnerGuard.check();
        return Result.ok(serviceItemService.list());
    }

    @PostMapping
    public Result<ServiceItemVO> create(@Valid @RequestBody ServiceItemRequest request) {
        OwnerGuard.check();
        return Result.ok(serviceItemService.create(request));
    }

    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortRequest request) {
        OwnerGuard.check();
        serviceItemService.sort(request.ids());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody ServiceItemRequest request) {
        OwnerGuard.check();
        serviceItemService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable long id, @Valid @RequestBody ServiceStatusRequest request) {
        OwnerGuard.check();
        serviceItemService.updateStatus(id, request.status());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        OwnerGuard.check();
        serviceItemService.delete(id);
        return Result.ok();
    }
}
