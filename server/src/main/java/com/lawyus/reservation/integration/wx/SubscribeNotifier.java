package com.lawyus.reservation.integration.wx;

import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.account.StaffAccount;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.store.Store;
import com.lawyus.reservation.beauty.store.mapper.StoreMapper;
import com.lawyus.reservation.common.security.PhoneCrypto;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.subscribe.SubscribeQuota;
import com.lawyus.reservation.kernel.subscribe.SubscribeTemplates;

import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;

/**
 * 预约订阅通知的业务封装：四类模板的接收方与内容组装。
 * 一律在事务提交后异步发送，避免回滚误发与拖慢提交；技师不接收「新预约通知」。
 */
@Slf4j
@Service
public class SubscribeNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int THING_MAX = 20;

    private final WxSubscribeService wxSubscribeService;
    private final ServiceItemMapper serviceItemMapper;
    private final StoreMapper storeMapper;
    private final StaffAccountMapper staffAccountMapper;
    private final PhoneCrypto phoneCrypto;
    private final TaskExecutor taskExecutor;

    public SubscribeNotifier(WxSubscribeService wxSubscribeService,
                             ServiceItemMapper serviceItemMapper,
                             StoreMapper storeMapper,
                             StaffAccountMapper staffAccountMapper,
                             PhoneCrypto phoneCrypto,
                             @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.wxSubscribeService = wxSubscribeService;
        this.serviceItemMapper = serviceItemMapper;
        this.storeMapper = storeMapper;
        this.staffAccountMapper = staffAccountMapper;
        this.phoneCrypto = phoneCrypto;
        this.taskExecutor = taskExecutor;
    }

    /** 提交成功：顾客收到预约成功通知，全部店长收到新预约通知。 */
    public void reservationCreated(Reservation reservation) {
        deliver(reservation, () -> {
            String serviceName = serviceName(reservation.getServiceId());
            String time = timeText(reservation);
            if (reservation.getUserId() != null) {
                wxSubscribeService.send(SubscribeQuota.USER_TYPE_USER, reservation.getUserId(),
                        SubscribeTemplates.RESERVATION_SUCCESS, userPage(reservation.getId()),
                        things("thing1", serviceName, "thing2", time, "thing3", storeAddress(reservation)));
            }
            String customer = reservation.getContactName() + " " + maskedPhone(reservation);
            for (StaffAccount owner : owners()) {
                wxSubscribeService.send(SubscribeQuota.USER_TYPE_STAFF, owner.getId(),
                        SubscribeTemplates.NEW_RESERVATION, staffPage(reservation.getId()),
                        things("thing1", serviceName, "thing2", time, "thing3", customer));
            }
        });
    }

    /** 状态变更：确认、拒绝、取消、改期与核销完成复用变更模板。 */
    public void reservationChanged(Reservation reservation, String changeType, String note) {
        if (reservation.getUserId() == null) {
            return;
        }
        deliver(reservation, () -> wxSubscribeService.send(SubscribeQuota.USER_TYPE_USER,
                reservation.getUserId(), SubscribeTemplates.RESERVATION_CHANGE, userPage(reservation.getId()),
                things("thing1", changeType,
                        "thing2", note == null || note.isBlank() ? timeText(reservation) : note,
                        "thing3", serviceName(reservation.getServiceId()))));
    }

    /** 到店提醒：开始前 reminder_hours 由 job 触发。 */
    public void reservationReminder(Reservation reservation) {
        if (reservation.getUserId() == null) {
            return;
        }
        deliver(reservation, () -> wxSubscribeService.send(SubscribeQuota.USER_TYPE_USER,
                reservation.getUserId(), SubscribeTemplates.RESERVATION_REMINDER, userPage(reservation.getId()),
                things("thing1", serviceName(reservation.getServiceId()),
                        "thing2", timeText(reservation),
                        "thing3", storeAddress(reservation))));
    }

    private void deliver(Reservation reservation, Runnable task) {
        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("[订阅] 通知异常 reservation={}", reservation.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(safeTask);
                }
            });
        } else {
            taskExecutor.execute(safeTask);
        }
    }

    private List<StaffAccount> owners() {
        return staffAccountMapper.selectList(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getRole, StaffAccount.ROLE_OWNER)
                .eq(StaffAccount::getStatus, StaffAccount.STATUS_ENABLED));
    }

    private String serviceName(long serviceId) {
        ServiceItem service = serviceItemMapper.selectById(serviceId);
        return service == null ? "预约服务" : service.getName();
    }

    private String storeAddress(Reservation reservation) {
        Store store = storeMapper.selectById(reservation.getStoreId());
        if (store == null || store.getAddress() == null) {
            return "本店";
        }
        return store.getAddress();
    }

    private String maskedPhone(Reservation reservation) {
        try {
            String phone = phoneCrypto.decrypt(reservation.getContactPhone());
            return phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : phone;
        } catch (Exception e) {
            return "";
        }
    }

    private String timeText(Reservation reservation) {
        return reservation.getDate().format(DateTimeFormatter.ofPattern("MM月dd日"))
                + " " + reservation.getStartTime().format(CLOCK)
                + "-" + reservation.getEndTime().format(CLOCK);
    }

    private String userPage(long reservationId) {
        return "pages/reservation/detail?id=" + reservationId;
    }

    private String staffPage(long reservationId) {
        return "pages/reservation/detail?id=" + reservationId;
    }

    private List<WxMaSubscribeMessage.MsgData> things(String... pairs) {
        List<WxMaSubscribeMessage.MsgData> data = new java.util.ArrayList<>(pairs.length / 2);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            data.add(new WxMaSubscribeMessage.MsgData(pairs[i], truncate(pairs[i + 1])));
        }
        return data;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > THING_MAX ? value.substring(0, THING_MAX - 1) + "…" : value;
    }
}
