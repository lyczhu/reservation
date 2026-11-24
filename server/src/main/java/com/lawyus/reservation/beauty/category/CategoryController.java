package com.lawyus.reservation.beauty.category;

import com.lawyus.reservation.beauty.category.dto.CategoryRequest;
import com.lawyus.reservation.beauty.category.dto.CategoryStatusRequest;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.dto.SortRequest;
import com.lawyus.reservation.common.security.OwnerGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务分类的增删改查、状态切换与排序接口，仅店长可访问。
 */
@RestController
@RequestMapping("/api/v1/b/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<ServiceCategory>> list() {
        OwnerGuard.check();
        return Result.ok(categoryService.list());
    }

    @PostMapping
    public Result<ServiceCategory> create(@Valid @RequestBody CategoryRequest request) {
        OwnerGuard.check();
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody SortRequest request) {
        OwnerGuard.check();
        categoryService.sort(request.ids());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody CategoryRequest request) {
        OwnerGuard.check();
        categoryService.update(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable long id, @Valid @RequestBody CategoryStatusRequest request) {
        OwnerGuard.check();
        categoryService.updateStatus(id, request.status());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        OwnerGuard.check();
        categoryService.delete(id);
        return Result.ok();
    }
}
