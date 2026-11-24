package com.lawyus.reservation.beauty.account;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.account.dto.StaffAccountCreateRequest;
import com.lawyus.reservation.beauty.account.dto.StaffAccountVO;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;

/**
 * 商家端账号管理：仅店长可用；新建仅限技师账号且必须绑定技师档案；
 * 停用即时踢下线，微信解绑要求账号先停用；全部写操作记审计。
 */
@Service
public class AccountService {

    private final StaffAccountMapper staffAccountMapper;
    private final TechnicianMapper technicianMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final StaffAuthService staffAuthService;

    public AccountService(StaffAccountMapper staffAccountMapper,
                          TechnicianMapper technicianMapper,
                          BCryptPasswordEncoder passwordEncoder,
                          AuditService auditService,
                          StaffAuthService staffAuthService) {
        this.staffAccountMapper = staffAccountMapper;
        this.technicianMapper = technicianMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.staffAuthService = staffAuthService;
    }

    public List<StaffAccountVO> list() {
        List<StaffAccount> accounts = staffAccountMapper.selectList(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getStoreId, AppConstants.STORE_ID)
                .orderByAsc(StaffAccount::getId));
        Set<Long> technicianIds = accounts.stream().map(StaffAccount::getTechnicianId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> technicianNames = technicianIds.isEmpty() ? Map.of()
                : technicianMapper.selectByIds(technicianIds).stream()
                        .collect(Collectors.toMap(Technician::getId, Technician::getName));
        return accounts.stream()
                .map(account -> toVO(account, technicianNames.get(account.getTechnicianId())))
                .toList();
    }

    @Transactional
    public StaffAccountVO create(StaffAccountCreateRequest request, long operatorId) {
        Technician technician = technicianMapper.selectById(request.technicianId());
        if (technician == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技师不存在");
        }
        if (staffAccountMapper.selectCount(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getTechnicianId, request.technicianId())) > 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该技师已绑定账号");
        }
        StaffAccount account = new StaffAccount();
        account.setStoreId(AppConstants.STORE_ID);
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(StaffAccount.ROLE_TECHNICIAN);
        account.setTechnicianId(request.technicianId());
        account.setStatus(StaffAccount.STATUS_ENABLED);
        try {
            staffAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "登录名已存在或该技师已绑定账号");
        }
        auditService.record(AuditService.OPERATOR_STAFF, operatorId, "ACCOUNT_CREATE", "staff_account",
                "创建账号 " + account.getUsername() + " 绑定技师 " + technician.getName());
        return toVO(account, technician.getName());
    }

    public void updateStatus(long id, int status, long operatorId) {
        StaffAccount account = requireExisting(id);
        if (account.getRole() == StaffAccount.ROLE_OWNER) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "店长账号不可停用");
        }
        StaffAccount update = new StaffAccount();
        update.setId(id);
        update.setStatus(status);
        staffAccountMapper.updateById(update);
        if (status == StaffAccount.STATUS_DISABLED) {
            StpStaffUtil.kickout(id);
        }
        auditService.record(AuditService.OPERATOR_STAFF, operatorId, "ACCOUNT_STATUS", "staff_account",
                "账号 " + account.getUsername() + (status == StaffAccount.STATUS_DISABLED ? " 停用" : " 启用"));
    }

    public void resetPassword(long id, String password, long operatorId) {
        StaffAccount account = requireExisting(id);
        StaffAccount update = new StaffAccount();
        update.setId(id);
        update.setPasswordHash(passwordEncoder.encode(password));
        staffAccountMapper.updateById(update);
        auditService.record(AuditService.OPERATOR_STAFF, operatorId, "PASSWORD_RESET", "staff_account",
                "账号 " + account.getUsername() + " 重置密码");
    }

    public void bindWx(long id, String code, long operatorId) {
        StaffAccount account = requireExisting(id);
        String openid = staffAuthService.resolveOpenid(code);
        try {
            staffAccountMapper.update(null, new LambdaUpdateWrapper<StaffAccount>()
                    .eq(StaffAccount::getId, id)
                    .set(StaffAccount::getWxOpenid, openid));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该微信已绑定其他账号");
        }
        auditService.record(AuditService.OPERATOR_STAFF, operatorId, "WX_BIND", "staff_account",
                "账号 " + account.getUsername() + " 绑定微信");
    }

    public void unbindWx(long id, long operatorId) {
        StaffAccount account = requireExisting(id);
        if (account.getStatus() != StaffAccount.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "请先停用账号再解绑微信");
        }
        if (account.getWxOpenid() == null) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "账号未绑定微信");
        }
        staffAccountMapper.update(null, new LambdaUpdateWrapper<StaffAccount>()
                .eq(StaffAccount::getId, id)
                .set(StaffAccount::getWxOpenid, null));
        auditService.record(AuditService.OPERATOR_STAFF, operatorId, "WX_UNBIND", "staff_account",
                "账号 " + account.getUsername() + " 解绑微信");
    }

    private StaffAccount requireExisting(long id) {
        StaffAccount account = staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getId, id)
                .eq(StaffAccount::getStoreId, AppConstants.STORE_ID));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        return account;
    }

    private StaffAccountVO toVO(StaffAccount account, String technicianName) {
        boolean owner = account.getRole() == StaffAccount.ROLE_OWNER;
        return new StaffAccountVO(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                owner ? "店长" : "技师",
                account.getTechnicianId(),
                technicianName,
                account.getWxOpenid() != null,
                account.getStatus(),
                account.getStatus() == StaffAccount.STATUS_ENABLED ? "启用" : "停用",
                account.getLastLoginAt(),
                account.getCreatedAt());
    }
}
