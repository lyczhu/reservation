package com.lawyus.reservation.customer.catalog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.category.ServiceCategory;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.ServiceTechnician;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceTechnicianMapper;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.store.Store;
import com.lawyus.reservation.beauty.store.mapper.RuleConfigMapper;
import com.lawyus.reservation.beauty.store.mapper.StoreMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.customer.catalog.dto.BusinessDayVO;
import com.lawyus.reservation.customer.catalog.dto.CategoryVO;
import com.lawyus.reservation.customer.catalog.dto.ServiceCardVO;
import com.lawyus.reservation.customer.catalog.dto.ServiceDetailVO;
import com.lawyus.reservation.customer.catalog.dto.StoreHomeVO;
import com.lawyus.reservation.customer.catalog.dto.StoreInfoVO;
import com.lawyus.reservation.customer.catalog.dto.TechnicianCardVO;

import tools.jackson.core.type.TypeReference;

/**
 * C 端浏览聚合：首页（门店/分类/热门/休息提示）、服务列表与详情；
 * 仅展示上架服务与启用分类，图片字段为 JSON 数组文本。
 */
@Service
public class CatalogService {

    private static final List<String> WEEK_LABELS = List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    private static final int HOT_LIMIT = 6;

    private final StoreMapper storeMapper;
    private final RuleConfigMapper ruleConfigMapper;
    private final ServiceCategoryMapper categoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServiceTechnicianMapper serviceTechnicianMapper;
    private final TechnicianMapper technicianMapper;
    private final JsonSupport jsonSupport;

    public CatalogService(StoreMapper storeMapper,
                          RuleConfigMapper ruleConfigMapper,
                          ServiceCategoryMapper categoryMapper,
                          ServiceItemMapper serviceItemMapper,
                          ServiceTechnicianMapper serviceTechnicianMapper,
                          TechnicianMapper technicianMapper,
                          JsonSupport jsonSupport) {
        this.storeMapper = storeMapper;
        this.ruleConfigMapper = ruleConfigMapper;
        this.categoryMapper = categoryMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.serviceTechnicianMapper = serviceTechnicianMapper;
        this.technicianMapper = technicianMapper;
        this.jsonSupport = jsonSupport;
    }

    public StoreHomeVO home() {
        Store store = storeMapper.selectById(AppConstants.STORE_ID);
        if (store == null) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "门店筹备中");
        }
        RuleConfig rule = ruleConfigMapper.selectById(AppConstants.STORE_ID);
        Map<String, List<TimeRange>> hours = jsonSupport.read(store.getBusinessHours(), new TypeReference<>() {
        });
        if (hours == null) {
            hours = Map.of();
        }
        List<BusinessDayVO> businessDays = new ArrayList<>(7);
        List<Integer> restDays = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            List<TimeRange> intervals = hours.getOrDefault(String.valueOf(weekday), List.of());
            if (intervals.isEmpty()) {
                restDays.add(weekday);
            }
            businessDays.add(new BusinessDayVO(weekday, WEEK_LABELS.get(weekday - 1), intervals));
        }
        List<String> banners = jsonSupport.read(store.getBanner(), new TypeReference<>() {
        });
        StoreInfoVO info = new StoreInfoVO(store.getName(), store.getLogo(),
                banners == null ? List.of() : banners, store.getAddress(), store.getLat(), store.getLng(),
                store.getPhone(), store.getAnnouncement(), businessDays, restDays);

        List<CategoryVO> categories = categoryMapper.selectList(new LambdaQueryWrapper<ServiceCategory>()
                        .eq(ServiceCategory::getStoreId, AppConstants.STORE_ID)
                        .eq(ServiceCategory::getStatus, ServiceCategory.STATUS_ENABLED)
                        .orderByAsc(ServiceCategory::getSort)
                        .orderByAsc(ServiceCategory::getId)).stream()
                .map(category -> new CategoryVO(category.getId(), category.getName(), category.getIcon()))
                .toList();

        List<ServiceCardVO> hotServices = serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItem>()
                        .eq(ServiceItem::getStoreId, AppConstants.STORE_ID)
                        .eq(ServiceItem::getStatus, ServiceItem.STATUS_ON)
                        .orderByDesc(ServiceItem::getBookedCount)
                        .orderByAsc(ServiceItem::getId)
                        .last("LIMIT " + HOT_LIMIT)).stream()
                .map(this::toCard)
                .toList();

        return new StoreHomeVO(info, categories, hotServices,
                rule == null ? 14 : rule.getBookableDays(), restNotice(restDays));
    }

    public List<ServiceCardVO> services(Long categoryId, String sort) {
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getStoreId, AppConstants.STORE_ID)
                .eq(ServiceItem::getStatus, ServiceItem.STATUS_ON)
                .eq(categoryId != null && categoryId > 0, ServiceItem::getCategoryId, categoryId);
        String order = sort == null || sort.isBlank() ? "default" : sort.trim();
        switch (order) {
            case "default" -> wrapper.orderByAsc(ServiceItem::getSort).orderByAsc(ServiceItem::getId);
            case "price" -> wrapper.orderByAsc(ServiceItem::getPriceCents).orderByAsc(ServiceItem::getId);
            case "hot" -> wrapper.orderByDesc(ServiceItem::getBookedCount).orderByAsc(ServiceItem::getId);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "sort 仅支持 default/price/hot");
        }
        return serviceItemMapper.selectList(wrapper).stream().map(this::toCard).toList();
    }

    public ServiceDetailVO detail(long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null || item.getStatus() != ServiceItem.STATUS_ON) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "服务不存在或已下架");
        }
        ServiceCategory category = categoryMapper.selectById(item.getCategoryId());
        List<String> images = jsonSupport.read(item.getImages(), new TypeReference<>() {
        });
        List<Long> technicianIds = serviceTechnicianMapper.selectList(new LambdaQueryWrapper<ServiceTechnician>()
                        .eq(ServiceTechnician::getServiceId, id)).stream()
                .map(ServiceTechnician::getTechnicianId)
                .toList();
        List<TechnicianCardVO> technicians = technicianIds.isEmpty() ? List.of()
                : technicianMapper.selectByIds(technicianIds).stream()
                        .filter(technician -> technician.getStatus() == Technician.STATUS_ENABLED)
                        .sorted(Comparator.comparingLong(Technician::getId))
                        .map(technician -> new TechnicianCardVO(technician.getId(), technician.getName(),
                                technician.getAvatar(), technician.getTitle()))
                        .toList();
        return new ServiceDetailVO(item.getId(), item.getCategoryId(),
                category == null ? "" : category.getName(), item.getName(), item.getCover(),
                images == null ? List.of() : images, item.getDurationMin(), item.getPriceCents(),
                item.getDescription(), item.getNotes(),
                item.getBookedCount() == null ? 0 : item.getBookedCount(), technicians);
    }

    private ServiceCardVO toCard(ServiceItem item) {
        return new ServiceCardVO(item.getId(), item.getCategoryId(), item.getName(), item.getCover(),
                item.getDurationMin(), item.getPriceCents(),
                item.getBookedCount() == null ? 0 : item.getBookedCount());
    }

    private String restNotice(List<Integer> restDays) {
        if (restDays.isEmpty()) {
            return null;
        }
        int today = LocalDate.now().getDayOfWeek().getValue();
        int tomorrow = LocalDate.now().plusDays(1).getDayOfWeek().getValue();
        if (restDays.contains(today)) {
            return "今日门店休息，欢迎预约其他日期";
        }
        if (restDays.contains(tomorrow)) {
            return "明日门店休息，请合理安排到店时间";
        }
        return "门店每周" + restDays.stream()
                .map(day -> WEEK_LABELS.get(day - 1))
                .collect(Collectors.joining("、")) + "休息";
    }
}
