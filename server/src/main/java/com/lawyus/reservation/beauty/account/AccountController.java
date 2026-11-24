package com.lawyus.reservation.beauty.account;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.account.dto.StaffAccountCreateRequest;
import com.lawyus.reservation.beauty.account.dto.StaffAccountStatusRequest;
import com.lawyus.reservation.beauty.account.dto.StaffAccountVO;
import com.lawyus.reservation.beauty.account.dto.StaffPasswordRequest;
import com.lawyus.reservation.beauty.account.dto.StaffWxCodeRequest;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.OwnerGuard;
import com.lawyus.reservation.common.security.StpStaffUtil;

import jakarta.validation.Valid;

/**
 * 账号管理接口（仅店长）：列表、创建技师账号、启停、重置密码、微信绑定/解绑。
 */
@RestController
@RequestMapping("/api/v1/b/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public Result<List<StaffAccountVO>> list() {
        OwnerGuard.check();
        return Result.ok(accountService.list());
    }

    @PostMapping
    public Result<StaffAccountVO> create(@Valid @RequestBody StaffAccountCreateRequest request) {
        OwnerGuard.check();
        return Result.ok(accountService.create(request, StpStaffUtil.getStaffId()));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable long id, @Valid @RequestBody StaffAccountStatusRequest request) {
        OwnerGuard.check();
        accountService.updateStatus(id, request.status(), StpStaffUtil.getStaffId());
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable long id, @Valid @RequestBody StaffPasswordRequest request) {
        OwnerGuard.check();
        accountService.resetPassword(id, request.password(), StpStaffUtil.getStaffId());
        return Result.ok();
    }

    @PostMapping("/{id}/bind-wx")
    public Result<Void> bindWx(@PathVariable long id, @Valid @RequestBody StaffWxCodeRequest request) {
        OwnerGuard.check();
        accountService.bindWx(id, request.code(), StpStaffUtil.getStaffId());
        return Result.ok();
    }

    @PostMapping("/{id}/unbind-wx")
    public Result<Void> unbindWx(@PathVariable long id) {
        OwnerGuard.check();
        accountService.unbindWx(id, StpStaffUtil.getStaffId());
        return Result.ok();
    }
}
