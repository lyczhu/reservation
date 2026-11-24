package com.lawyus.reservation.beauty.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lawyus.reservation.beauty.category.ServiceCategory;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.dto.ServiceItemRequest;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceTechnicianMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1：服务写入面审计测试（改价 / 上下架 / 排序落审计，无变更不落审计）。 */
@ExtendWith(MockitoExtension.class)
class ServiceItemServiceAuditTest {

    @Mock ServiceItemMapper serviceItemMapper;
    @Mock ServiceTechnicianMapper serviceTechnicianMapper;
    @Mock ServiceCategoryMapper categoryMapper;
    @Mock TechnicianMapper technicianMapper;
    @Mock ReservationMapper reservationMapper;
    @Mock JsonSupport jsonSupport;
    @Mock AuditService auditService;

    ServiceItemService service;
    MockedStatic<StpStaffUtil> stpMock;

    private static ServiceItem existingService() {
        ServiceItem item = new ServiceItem();
        item.setId(12L);
        item.setCategoryId(2L);
        item.setName("基础护理");
        item.setCover("/cover.png");
        item.setImages("[]");
        item.setDurationMin(60);
        item.setPriceCents(19800);
        item.setSort(0);
        item.setStatus(ServiceItem.STATUS_ON);
        return item;
    }

    private static ServiceCategory enabledCategory() {
        ServiceCategory category = new ServiceCategory();
        category.setId(2L);
        category.setStatus(ServiceCategory.STATUS_ENABLED);
        return category;
    }

    private static Technician enabledTechnician(long id) {
        Technician technician = new Technician();
        technician.setId(id);
        technician.setStatus(Technician.STATUS_ENABLED);
        return technician;
    }

    private static ServiceTechnician relation(long technicianId) {
        ServiceTechnician relation = new ServiceTechnician();
        relation.setServiceId(12L);
        relation.setTechnicianId(technicianId);
        return relation;
    }

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ServiceItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new ServiceItemService(serviceItemMapper, serviceTechnicianMapper, categoryMapper,
                technicianMapper, reservationMapper, jsonSupport, auditService);
        stpMock = mockStatic(StpStaffUtil.class);
        stpMock.when(StpStaffUtil::getStaffId).thenReturn(88L);
    }

    @AfterEach
    void tearDown() {
        stpMock.close();
    }

    private ServiceItemRequest requestWithPrice(int price) {
        return new ServiceItemRequest(2L, "基础护理", "/cover.png", List.of(), 60, price, null, null, 0, List.of(3L));
    }

    private void stubCommonDependencies() {
        when(serviceItemMapper.selectById(12L)).thenReturn(existingService());
        when(categoryMapper.selectById(2L)).thenReturn(enabledCategory());
        when(technicianMapper.selectByIds(List.of(3L))).thenReturn(List.of(enabledTechnician(3L)));
        when(serviceTechnicianMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(relation(3L)));
    }

    @Test
    void update_whenPriceChanged_recordsSERVICE_UPDATEWithBeforeAfter() {
        stubCommonDependencies();

        service.update(12L, requestWithPrice(22800));

        verify(auditService).record(eq(AuditService.OPERATOR_STAFF), eq(88L), eq("SERVICE_UPDATE"),
                eq("service:12"), argThat(detail -> detail.contains("priceCents 19800->22800")));
    }

    @Test
    void update_whenNothingChanged_doesNotRecordAudit() {
        stubCommonDependencies();

        service.update(12L, requestWithPrice(19800));

        verify(auditService, never()).record(anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    void updateStatus_recordsSERVICE_STATUSWithBeforeAfter() {
        when(serviceItemMapper.selectById(12L)).thenReturn(existingService());

        service.updateStatus(12L, ServiceItem.STATUS_OFF);

        verify(auditService).record(eq(AuditService.OPERATOR_STAFF), eq(88L), eq("SERVICE_STATUS"),
                eq("service:12"), argThat(detail -> detail.contains("status 1->2")));
    }

    @Test
    void sort_recordsSERVICE_SORT() {
        service.sort(List.of(12L, 13L));

        verify(auditService).record(eq(AuditService.OPERATOR_STAFF), eq(88L), eq("SERVICE_SORT"),
                eq("service"), argThat(detail -> detail.contains("12") && detail.contains("13")));
    }
}
