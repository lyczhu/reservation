package com.lawyus.reservation.beauty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.category.ServiceCategory;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.dto.ServiceItemRequest;
import com.lawyus.reservation.beauty.service.dto.ServiceItemVO;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceTechnicianMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务管理：可约技师以 service_technician 为唯一事实来源，保存时整体重写关联；
 * 存在历史预约的服务仅允许下架，不可删除。
 */
@Service
public class ServiceItemService {

    private final ServiceItemMapper serviceItemMapper;
    private final ServiceTechnicianMapper serviceTechnicianMapper;
    private final ServiceCategoryMapper categoryMapper;
    private final TechnicianMapper technicianMapper;
    private final ReservationMapper reservationMapper;
    private final JsonSupport jsonSupport;
    private final AuditService auditService;

    public ServiceItemService(ServiceItemMapper serviceItemMapper,
                              ServiceTechnicianMapper serviceTechnicianMapper,
                              ServiceCategoryMapper categoryMapper,
                              TechnicianMapper technicianMapper,
                              ReservationMapper reservationMapper,
                              JsonSupport jsonSupport,
                              AuditService auditService) {
        this.serviceItemMapper = serviceItemMapper;
        this.serviceTechnicianMapper = serviceTechnicianMapper;
        this.categoryMapper = categoryMapper;
        this.technicianMapper = technicianMapper;
        this.reservationMapper = reservationMapper;
        this.jsonSupport = jsonSupport;
        this.auditService = auditService;
    }

    public List<ServiceItemVO> list() {
        List<ServiceItem> items = serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItem>()
                .orderByAsc(ServiceItem::getSort)
                .orderByAsc(ServiceItem::getId));
        Map<Long, List<Long>> technicianMap = serviceTechnicianMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ServiceTechnician::getServiceId,
                        Collectors.mapping(ServiceTechnician::getTechnicianId, Collectors.toList())));
        return items.stream()
                .map(item -> toVO(item, technicianMap.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ServiceItemVO create(ServiceItemRequest request) {
        validate(request);
        ServiceItem item = new ServiceItem();
        item.setStoreId(AppConstants.STORE_ID);
        item.setCategoryId(request.categoryId());
        item.setName(request.name());
        item.setCover(request.cover());
        item.setImages(jsonSupport.write(request.images() == null ? List.of() : request.images()));
        item.setDurationMin(request.durationMin());
        item.setPriceCents(request.priceCents());
        item.setDescription(request.description());
        item.setNotes(request.notes());
        item.setSort(request.sort() == null ? 0 : request.sort());
        item.setStatus(ServiceItem.STATUS_ON);
        item.setDeleted(0);
        item.setBookedCount(0);
        serviceItemMapper.insert(item);
        saveTechnicians(item.getId(), request.technicianIds());
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SERVICE_CREATE",
                "service:" + item.getId(), "服务 " + request.name() + "，价格 " + request.priceCents()
                        + " 分，时长 " + request.durationMin() + " 分钟");
        return toVO(item, request.technicianIds());
    }

    @Transactional
    public void update(long id, ServiceItemRequest request) {
        ServiceItem old = getExisting(id);
        validate(request);
        List<Long> oldTechnicianIds = serviceTechnicianMapper.selectList(new LambdaQueryWrapper<ServiceTechnician>()
                        .eq(ServiceTechnician::getServiceId, id)).stream()
                .map(ServiceTechnician::getTechnicianId).sorted().toList();
        serviceItemMapper.update(null, new LambdaUpdateWrapper<ServiceItem>()
                .eq(ServiceItem::getId, id)
                .set(ServiceItem::getCategoryId, request.categoryId())
                .set(ServiceItem::getName, request.name())
                .set(ServiceItem::getCover, request.cover())
                .set(ServiceItem::getImages, jsonSupport.write(request.images() == null ? List.of() : request.images()))
                .set(ServiceItem::getDurationMin, request.durationMin())
                .set(ServiceItem::getPriceCents, request.priceCents())
                .set(ServiceItem::getDescription, request.description())
                .set(ServiceItem::getNotes, request.notes())
                .set(ServiceItem::getSort, request.sort() == null ? 0 : request.sort()));
        saveTechnicians(id, request.technicianIds());

        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", old.getName(), request.name());
        AuditService.appendChange(detail, "categoryId", old.getCategoryId(), request.categoryId());
        AuditService.appendChange(detail, "priceCents", old.getPriceCents(), request.priceCents());
        AuditService.appendChange(detail, "durationMin", old.getDurationMin(), request.durationMin());
        AuditService.appendChange(detail, "sort", old.getSort(), request.sort() == null ? 0 : request.sort());
        AuditService.appendChange(detail, "technicianIds", oldTechnicianIds,
                request.technicianIds().stream().distinct().sorted().toList());
        if (detail.length() > 0) {
            auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SERVICE_UPDATE",
                    "service:" + id, detail.toString());
        }
    }

    public void updateStatus(long id, int status) {
        ServiceItem old = getExisting(id);
        ServiceItem update = new ServiceItem();
        update.setId(id);
        update.setStatus(status);
        serviceItemMapper.updateById(update);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SERVICE_STATUS",
                "service:" + id, "服务 " + old.getName() + " status " + old.getStatus() + "->" + status);
    }

    @Transactional
    public void delete(long id) {
        ServiceItem old = getExisting(id);
        if (reservationMapper.countByServiceId(id) > 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "服务存在历史预约，仅可下架");
        }
        serviceTechnicianMapper.delete(new LambdaQueryWrapper<ServiceTechnician>()
                .eq(ServiceTechnician::getServiceId, id));
        serviceItemMapper.deleteById(id);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SERVICE_DELETE",
                "service:" + id, "删除服务 " + old.getName());
    }

    public void sort(List<Long> ids) {
        int sort = 10;
        for (Long id : ids) {
            ServiceItem update = new ServiceItem();
            update.setId(id);
            update.setSort(sort);
            serviceItemMapper.updateById(update);
            sort += 10;
        }
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SERVICE_SORT",
                "service", "服务排序调整为 " + ids);
    }

    private void validate(ServiceItemRequest request) {
        if (request.durationMin() % 15 != 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "durationMin 须为 15 的倍数");
        }
        ServiceCategory category = categoryMapper.selectById(request.categoryId());
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        if (category.getStatus() != ServiceCategory.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "分类已停用");
        }
        List<Long> technicianIds = request.technicianIds().stream().distinct().toList();
        List<Technician> technicians = technicianMapper.selectByIds(technicianIds);
        if (technicians.size() != technicianIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技师不存在");
        }
        if (technicians.stream().anyMatch(technician -> technician.getStatus() != Technician.STATUS_ENABLED)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "技师已停用");
        }
    }

    private void saveTechnicians(long serviceId, List<Long> technicianIds) {
        serviceTechnicianMapper.delete(new LambdaQueryWrapper<ServiceTechnician>()
                .eq(ServiceTechnician::getServiceId, serviceId));
        for (Long technicianId : technicianIds.stream().distinct().toList()) {
            ServiceTechnician relation = new ServiceTechnician();
            relation.setServiceId(serviceId);
            relation.setTechnicianId(technicianId);
            serviceTechnicianMapper.insert(relation);
        }
    }

    private ServiceItem getExisting(long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务不存在");
        }
        return item;
    }

    private ServiceItemVO toVO(ServiceItem item, List<Long> technicianIds) {
        List<String> images = jsonSupport.read(item.getImages(), new TypeReference<>() {
        });
        return new ServiceItemVO(item.getId(), item.getCategoryId(), item.getName(), item.getCover(), images,
                item.getDurationMin(), item.getPriceCents(), item.getDescription(), item.getNotes(),
                item.getSort(), item.getStatus(), item.getBookedCount(), technicianIds);
    }
}
