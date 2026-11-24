package com.lawyus.reservation.beauty.category;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.category.dto.CategoryRequest;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类管理：停用或删除前校验分类下无未删除服务，避免产生孤立服务。
 */
@Service
public class CategoryService {

    private final ServiceCategoryMapper categoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final AuditService auditService;

    public CategoryService(ServiceCategoryMapper categoryMapper, ServiceItemMapper serviceItemMapper,
                           AuditService auditService) {
        this.categoryMapper = categoryMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.auditService = auditService;
    }

    public List<ServiceCategory> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ServiceCategory>()
                .orderByAsc(ServiceCategory::getSort)
                .orderByAsc(ServiceCategory::getId));
    }

    public ServiceCategory create(CategoryRequest request) {
        ServiceCategory category = new ServiceCategory();
        category.setStoreId(AppConstants.STORE_ID);
        category.setName(request.name());
        category.setIcon(request.icon());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(ServiceCategory.STATUS_ENABLED);
        category.setDeleted(0);
        categoryMapper.insert(category);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "CATEGORY_CREATE",
                "category:" + category.getId(), "创建分类 " + category.getName());
        return category;
    }

    public void update(long id, CategoryRequest request) {
        ServiceCategory old = getExisting(id);
        ServiceCategory update = new ServiceCategory();
        update.setId(id);
        update.setName(request.name());
        update.setIcon(request.icon());
        update.setSort(request.sort());
        categoryMapper.updateById(update);
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", old.getName(), request.name());
        AuditService.appendChange(detail, "sort", old.getSort(), request.sort());
        if (detail.length() > 0) {
            auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "CATEGORY_UPDATE",
                    "category:" + id, detail.toString());
        }
    }

    public void updateStatus(long id, int status) {
        ServiceCategory old = getExisting(id);
        if (status == ServiceCategory.STATUS_DISABLED && countServices(id) > 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "分类下存在服务，不能停用");
        }
        ServiceCategory update = new ServiceCategory();
        update.setId(id);
        update.setStatus(status);
        categoryMapper.updateById(update);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "CATEGORY_STATUS",
                "category:" + id, "分类 " + old.getName() + " status " + old.getStatus() + "->" + status);
    }

    public void delete(long id) {
        ServiceCategory old = getExisting(id);
        if (countServices(id) > 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "分类下存在服务，不能删除");
        }
        categoryMapper.deleteById(id);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "CATEGORY_DELETE",
                "category:" + id, "删除分类 " + old.getName());
    }

    public void sort(List<Long> ids) {
        int sort = 10;
        for (Long id : ids) {
            ServiceCategory update = new ServiceCategory();
            update.setId(id);
            update.setSort(sort);
            categoryMapper.updateById(update);
            sort += 10;
        }
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "CATEGORY_SORT",
                "category", "分类排序调整为 " + ids);
    }

    private ServiceCategory getExisting(long id) {
        ServiceCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private long countServices(long categoryId) {
        return serviceItemMapper.selectCount(new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getCategoryId, categoryId));
    }
}
