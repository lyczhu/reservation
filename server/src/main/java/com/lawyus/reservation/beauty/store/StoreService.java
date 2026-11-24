package com.lawyus.reservation.beauty.store;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.store.dto.StoreUpdateRequest;
import com.lawyus.reservation.beauty.store.dto.StoreVO;
import com.lawyus.reservation.beauty.store.mapper.StoreMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.common.validation.TimeRangeValidator;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 门店资料读写：businessHours 以 weekday（"1"-"7"）为键，区间数组为空表示当日休息。
 */
@Service
public class StoreService {

    private static final Set<String> WEEKDAYS = Set.of("1", "2", "3", "4", "5", "6", "7");

    private final StoreMapper storeMapper;
    private final JsonSupport jsonSupport;
    private final AuditService auditService;

    public StoreService(StoreMapper storeMapper, JsonSupport jsonSupport, AuditService auditService) {
        this.storeMapper = storeMapper;
        this.jsonSupport = jsonSupport;
        this.auditService = auditService;
    }

    public StoreVO get() {
        Store store = storeMapper.selectById(AppConstants.STORE_ID);
        if (store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "门店不存在");
        }
        List<String> banner = jsonSupport.read(store.getBanner(), new TypeReference<>() {
        });
        Map<String, List<TimeRange>> businessHours = jsonSupport.read(store.getBusinessHours(), new TypeReference<>() {
        });
        return new StoreVO(store.getId(), store.getName(), store.getLogo(), banner, store.getAddress(),
                store.getLat(), store.getLng(), store.getPhone(), businessHours, store.getAnnouncement(),
                store.getStatus());
    }

    public void update(StoreUpdateRequest request) {
        validateBusinessHours(request.businessHours());
        Store old = storeMapper.selectById(AppConstants.STORE_ID);
        if (old == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "门店不存在");
        }
        String businessHoursJson = jsonSupport.write(request.businessHours());
        storeMapper.update(null, new LambdaUpdateWrapper<Store>()
                .eq(Store::getId, AppConstants.STORE_ID)
                .set(Store::getName, request.name())
                .set(Store::getLogo, request.logo())
                .set(Store::getBanner, jsonSupport.write(request.banner() == null ? List.of() : request.banner()))
                .set(Store::getAddress, request.address())
                .set(Store::getLat, request.lat())
                .set(Store::getLng, request.lng())
                .set(Store::getPhone, request.phone())
                .set(Store::getBusinessHours, businessHoursJson)
                .set(Store::getAnnouncement, request.announcement()));

        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", old.getName(), request.name());
        AuditService.appendChange(detail, "announcement", old.getAnnouncement(), request.announcement());
        if (!Objects.equals(old.getBusinessHours(), businessHoursJson)) {
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append("businessHours 已更新");
        }
        if (detail.length() == 0) {
            detail.append("门店资料已提交（无字段变化）");
        }
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "STORE_UPDATE",
                "store:" + AppConstants.STORE_ID, detail.toString());
    }

    private void validateBusinessHours(Map<String, List<TimeRange>> businessHours) {
        for (Map.Entry<String, List<TimeRange>> entry : businessHours.entrySet()) {
            if (!WEEKDAYS.contains(entry.getKey())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "businessHours 键必须为 1-7");
            }
            TimeRangeValidator.validate(entry.getValue());
        }
    }
}
