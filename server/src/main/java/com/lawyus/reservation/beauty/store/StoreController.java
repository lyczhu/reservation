package com.lawyus.reservation.beauty.store;

import com.lawyus.reservation.beauty.store.dto.RuleUpdateRequest;
import com.lawyus.reservation.beauty.store.dto.StoreUpdateRequest;
import com.lawyus.reservation.beauty.store.dto.StoreVO;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.OwnerGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店资料与预约规则的查询、编辑接口，仅店长可访问。
 */
@RestController
@RequestMapping("/api/v1/b")
public class StoreController {

    private final StoreService storeService;
    private final RuleService ruleService;

    public StoreController(StoreService storeService, RuleService ruleService) {
        this.storeService = storeService;
        this.ruleService = ruleService;
    }

    @GetMapping("/store")
    public Result<StoreVO> getStore() {
        OwnerGuard.check();
        return Result.ok(storeService.get());
    }

    @PutMapping("/store")
    public Result<Void> updateStore(@Valid @RequestBody StoreUpdateRequest request) {
        OwnerGuard.check();
        storeService.update(request);
        return Result.ok();
    }

    @GetMapping("/rules")
    public Result<RuleConfig> getRules() {
        OwnerGuard.check();
        return Result.ok(ruleService.get());
    }

    @PutMapping("/rules")
    public Result<Void> updateRules(@Valid @RequestBody RuleUpdateRequest request) {
        OwnerGuard.check();
        ruleService.update(request);
        return Result.ok();
    }
}
