package com.lawyus.reservation.customer.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.customer.catalog.dto.ServiceCardVO;
import com.lawyus.reservation.customer.catalog.dto.ServiceDetailVO;
import com.lawyus.reservation.customer.catalog.dto.StoreHomeVO;

/**
 * C 端浏览接口（匿名）：首页聚合、服务列表与详情。
 */
@RestController
@RequestMapping("/api/v1/c")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/store/home")
    public Result<StoreHomeVO> home() {
        return Result.ok(catalogService.home());
    }

    @GetMapping("/services")
    public Result<List<ServiceCardVO>> services(@RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String sort) {
        return Result.ok(catalogService.services(categoryId, sort));
    }

    @GetMapping("/services/{id}")
    public Result<ServiceDetailVO> detail(@PathVariable long id) {
        return Result.ok(catalogService.detail(id));
    }
}
