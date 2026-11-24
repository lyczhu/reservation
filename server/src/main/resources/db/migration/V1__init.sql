-- =====================================================================
-- 轻量美业预约小程序 V1.0 建表脚本
-- 文档编号: TD-RSV-2026-001 配套产物
-- 需求依据: docs/预约小程序PRD文档.md（第 7 章数据字典）
-- 数据库:   MySQL 8.0+ / InnoDB / utf8mb4
-- 说明:     纯 DDL，不含业务数据（账号由部署脚本初始化）。
--           纳入 Flyway 时作为 V1__init.sql。
-- 关键方言: active_slot 为 STORED 生成列，实现"活跃态唯一约束"（MySQL 无部分索引）。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. store 门店（单店版仅一行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `store` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '门店ID',
  `name`           VARCHAR(50)   NOT NULL COMMENT '门店名称',
  `logo`           VARCHAR(500)  NULL COMMENT 'Logo URL',
  `banner`         JSON          NULL COMMENT 'Banner 图数组，≤3 张',
  `address`        VARCHAR(200)  NULL COMMENT '门店地址',
  `lat`            DECIMAL(10,7) NULL COMMENT '纬度（导航用）',
  `lng`            DECIMAL(10,7) NULL COMMENT '经度（导航用）',
  `phone`          VARCHAR(20)   NULL COMMENT '门店电话',
  `business_hours` JSON          NOT NULL COMMENT '按周营业时间 {weekday:[{start,end}]}，空数组=休息日',
  `announcement`   VARCHAR(60)   NULL COMMENT '公告，≤60 字',
  `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '1营业 2停用',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';

-- ---------------------------------------------------------------------
-- 2. rule_config 预约规则（单店一行，字段与商家端可配置的规则参数一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rule_config` (
  `store_id`                 BIGINT   NOT NULL COMMENT '门店ID（单店一行）',
  `slot_granularity`         SMALLINT NOT NULL DEFAULT 30 COMMENT '时段粒度(分钟) 30/60',
  `bookable_days`            SMALLINT NOT NULL DEFAULT 14 COMMENT '可约天数 7-30',
  `min_lead_minutes`         SMALLINT NOT NULL DEFAULT 60 COMMENT '最小提前量(分钟) 0-1440',
  `cancel_deadline_hours`    SMALLINT NOT NULL DEFAULT 2 COMMENT '取消时限(小时) 0-24',
  `reschedule_deadline_hours` SMALLINT NOT NULL DEFAULT 2 COMMENT '改期时限(小时) 0-24',
  `need_confirm`             TINYINT  NOT NULL DEFAULT 1 COMMENT '需商家确认 1是 0否',
  `max_concurrent`           TINYINT  NOT NULL DEFAULT 1 COMMENT '每时段最大接待，V1.0 固定为 1',
  `buffer_minutes`           SMALLINT NOT NULL DEFAULT 0 COMMENT '服务缓冲(分钟) 0-30',
  `reminder_hours`           SMALLINT NOT NULL DEFAULT 2 COMMENT '到店提醒提前(小时) 1-24',
  `created_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`store_id`),
  CONSTRAINT `chk_max_concurrent` CHECK (`max_concurrent` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约规则配置';

-- ---------------------------------------------------------------------
-- 3. service_category 服务分类
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service_category` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `store_id`   BIGINT      NOT NULL COMMENT '门店ID',
  `name`       VARCHAR(10) NOT NULL COMMENT '分类名称，≤10 字',
  `icon`       VARCHAR(500) NOT NULL COMMENT '导航图标 URL',
  `sort`       INT         NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  `status`     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否 1是',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_status` (`store_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务分类';

-- ---------------------------------------------------------------------
-- 4. service 服务（可约技师不落本表，由 service_technician 维护）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `store_id`     BIGINT       NOT NULL COMMENT '门店ID',
  `category_id`  BIGINT       NOT NULL COMMENT '分类ID',
  `name`         VARCHAR(30)  NOT NULL COMMENT '服务名称，≤30 字',
  `cover`        VARCHAR(500) NOT NULL COMMENT '封面图 URL',
  `images`       JSON         NULL COMMENT '详情图数组，≤9 张',
  `duration_min` INT          NOT NULL COMMENT '时长(分钟) 15-240，步进15',
  `price_cents`  INT          NOT NULL COMMENT '价格(分)，≥0',
  `description`  TEXT         NULL COMMENT '服务说明（富文本，≤500 字）',
  `notes`        VARCHAR(600) NULL COMMENT '注意事项，≤200 字',
  `sort`         INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 2下架',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否 1是',
  `booked_count` INT          NOT NULL DEFAULT 0 COMMENT '已约数（历史已完成，每日重算）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_category` (`store_id`, `category_id`, `status`, `deleted`),
  CONSTRAINT `chk_duration` CHECK (`duration_min` BETWEEN 15 AND 240),
  CONSTRAINT `chk_price` CHECK (`price_cents` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务';

-- ---------------------------------------------------------------------
-- 5. service_technician 服务-技师关联（唯一事实来源）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service_technician` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `service_id`    BIGINT NOT NULL COMMENT '服务ID',
  `technician_id` BIGINT NOT NULL COMMENT '技师ID',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_technician` (`service_id`, `technician_id`),
  KEY `idx_technician` (`technician_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务-技师关联（唯一事实来源）';

-- ---------------------------------------------------------------------
-- 6. technician 技师（擅长服务由 service_technician 推导，不落本表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `technician` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `store_id`   BIGINT       NOT NULL COMMENT '门店ID',
  `name`       VARCHAR(20)  NOT NULL COMMENT '姓名，≤20 字',
  `avatar`     VARCHAR(500) NOT NULL COMMENT '头像 URL',
  `title`      VARCHAR(20)  NULL COMMENT '职称，≤20 字',
  `intro`      VARCHAR(600) NULL COMMENT '简介，≤200 字',
  `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 2停用（停用不可被预约）',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否 1是',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_status` (`store_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师';

-- ---------------------------------------------------------------------
-- 7. shift 排班（周模板 + 日覆盖，日覆盖优先）
--    唯一性（一技师一周几一行 / 一技师一日一行）由应用层 upsert 保证。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shift` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `technician_id` BIGINT      NOT NULL COMMENT '技师ID',
  `scope`         ENUM('week','date') NOT NULL COMMENT 'week周模板 date日覆盖',
  `weekday`       TINYINT     NULL COMMENT '周几 1-7（scope=week 时有效）',
  `date`          DATE        NULL COMMENT '具体日期（scope=date 时有效）',
  `intervals`     JSON        NOT NULL COMMENT '班次时段数组 [{start,end}]，可多段',
  `type`          ENUM('work','rest','leave') NOT NULL DEFAULT 'work' COMMENT 'work工作 rest休息 leave请假',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tech_week` (`technician_id`, `scope`, `weekday`),
  KEY `idx_tech_date` (`technician_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班';

-- ---------------------------------------------------------------------
-- 8. user 顾客（手机号 AES 密文 + HMAC 哈希索引）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `openid`     VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  `nickname`   VARCHAR(50)  NULL COMMENT '昵称',
  `avatar`     VARCHAR(500) NULL COMMENT '头像 URL',
  `phone`      VARCHAR(500) NULL COMMENT '手机号 AES-256-GCM 密文 base64(iv+ciphertext)',
  `phone_hash` CHAR(64)     NULL COMMENT '手机号 HMAC-SHA256 哈希（精确查询索引）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='顾客';

-- ---------------------------------------------------------------------
-- 9. staff_account 商家端账号（店长/技师）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `staff_account` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `store_id`      BIGINT       NOT NULL COMMENT '门店ID',
  `username`      VARCHAR(20)  NOT NULL COMMENT '登录名（手机号格式），门店内唯一',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'bcrypt 哈希',
  `role`          TINYINT      NOT NULL COMMENT '1店长(owner) 2技师(technician)',
  `technician_id` BIGINT       NULL COMMENT 'role=2 时必填且唯一；店长为空',
  `wx_openid`     VARCHAR(64)  NULL COMMENT '商家端微信绑定 openid，可空，唯一',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
  `last_login_at` DATETIME     NULL COMMENT '最近登录时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_username` (`store_id`, `username`),
  UNIQUE KEY `uk_technician` (`technician_id`),
  UNIQUE KEY `uk_wx_openid` (`wx_openid`),
  KEY `idx_store_role` (`store_id`, `role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家端账号';

-- ---------------------------------------------------------------------
-- 10. reservation 预约单（核心）
--     active_slot 生成列：活跃态(1待确认/2待服务)=1，终态=NULL。
--     两个唯一约束借助"NULL 不参与唯一"实现仅约束活跃单。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reservation` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
  `no`                 VARCHAR(20) NOT NULL COMMENT '预约号 RSV+yyyyMMdd+4位流水',
  `store_id`           BIGINT      NOT NULL COMMENT '门店ID',
  `user_id`            BIGINT      NULL COMMENT '顾客ID，代客单为空',
  `service_id`         BIGINT      NOT NULL COMMENT '服务ID',
  `technician_id`      BIGINT      NOT NULL COMMENT '技师ID',
  `date`               DATE        NOT NULL COMMENT '预约日期',
  `start_time`         TIME        NOT NULL COMMENT '开始时间',
  `end_time`           TIME        NOT NULL COMMENT '结束时间=start+时长，不含缓冲',
  `status`             TINYINT     NOT NULL COMMENT '1待确认 2待服务 3已完成 4已取消 5未到店',
  `active_slot`        TINYINT     GENERATED ALWAYS AS (IF(`status` IN (1,2), 1, NULL)) STORED COMMENT '活跃态标记（生成列）',
  `contact_name`       VARCHAR(20) NOT NULL COMMENT '联系人姓名',
  `contact_phone`      VARCHAR(500) NOT NULL COMMENT '联系电话 AES 密文',
  `contact_phone_hash` CHAR(64)    NOT NULL COMMENT '联系电话 HMAC 哈希（精确查询）',
  `remark`             VARCHAR(300) NULL COMMENT '备注，≤100 字',
  `source`             TINYINT     NOT NULL DEFAULT 1 COMMENT '1自助(self) 2代客(proxy)',
  `verify_code`        CHAR(6)     NOT NULL COMMENT '核销码 HMAC 截断6位，活跃单内唯一',
  `idempotency_key`    VARCHAR(36) NULL COMMENT '客户端幂等键 UUID，自助单防重',
  `rule_snapshot`      JSON        NOT NULL COMMENT '创建时规则快照',
  `reminded_at`        DATETIME    NULL COMMENT '到店提醒发送时间（实现层补充字段）',
  `version`            INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `confirmed_at`       DATETIME    NULL COMMENT '确认时间',
  `completed_at`       DATETIME    NULL COMMENT '完成时间',
  `cancelled_at`       DATETIME    NULL COMMENT '取消时间',
  `cancel_reason`      VARCHAR(150) NULL COMMENT '取消原因',
  `updated_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`no`),
  UNIQUE KEY `uk_slot_active` (`technician_id`, `date`, `start_time`, `active_slot`),
  UNIQUE KEY `uk_verify_active` (`verify_code`, `active_slot`),
  UNIQUE KEY `uk_idempotency` (`idempotency_key`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_store_date` (`store_id`, `date`),
  KEY `idx_phone_hash` (`contact_phone_hash`),
  KEY `idx_reminder` (`status`, `date`, `start_time`, `reminded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约单';

-- ---------------------------------------------------------------------
-- 11. reservation_log 操作留痕（含未到店时间与原因）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reservation_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `reservation_id` BIGINT       NOT NULL COMMENT '预约单ID',
  `action`         VARCHAR(30)  NOT NULL COMMENT '动作：submit/confirm/reject/cancel/reschedule/verify/no_show',
  `operator_role`  TINYINT      NOT NULL COMMENT '操作人角色 1顾客 2技师 3店长 4系统',
  `operator_id`    BIGINT       NULL COMMENT '操作人ID（user.id 或 staff_account.id）',
  `detail`         VARCHAR(1000) NULL COMMENT '详情（原因/改期前后时段等）',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_reservation` (`reservation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约操作留痕';

-- ---------------------------------------------------------------------
-- 12. subscribe_quota 订阅消息配额（授权+1 发送-1，>0 才发）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `subscribe_quota` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `user_type`    TINYINT     NOT NULL COMMENT '1顾客(user.id) 2商家账号(staff_account.id)',
  `user_id`      BIGINT      NOT NULL COMMENT '对应用户ID',
  `template_key` VARCHAR(50) NOT NULL COMMENT '模板标识',
  `remaining`    INT         NOT NULL DEFAULT 0 COMMENT '剩余可发次数',
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_template` (`user_type`, `user_id`, `template_key`),
  CONSTRAINT `chk_remaining` CHECK (`remaining` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅消息配额';

-- ---------------------------------------------------------------------
-- 13. audit_log 审计日志（敏感操作：查看完整手机号/规则变更/账号管理/核销锁定）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `store_id`     BIGINT       NOT NULL COMMENT '门店ID',
  `operator_type` TINYINT     NOT NULL COMMENT '操作人类型 1顾客 2商家账号',
  `operator_id`  BIGINT       NOT NULL COMMENT '操作人ID',
  `action`       VARCHAR(50)  NOT NULL COMMENT '审计动作',
  `target`       VARCHAR(100) NULL COMMENT '操作对象（如预约号/账号ID）',
  `detail`       VARCHAR(1000) NULL COMMENT '详情',
  `ip`           VARCHAR(45)  NULL COMMENT '来源IP',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_time` (`store_id`, `created_at`),
  KEY `idx_operator` (`operator_type`, `operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 设计备注
-- 1. 外键：当前以应用层保证引用完整性，未建物理外键（便于数据重置）。
-- 2. active_slot 生成列为 MySQL 8 方言，迁移其它数据库需重设活跃态唯一约束。
-- 3. uk_slot_active 前缀 (technician_id, date) 支撑时段引擎"某技师某日活跃单"查询，
--    并在 RR 隔离级别下为 SELECT ... FOR UPDATE 提供间隙锁基础。
-- 4. uk_verify_active 保证核销码仅在活跃单内唯一；终态单 active_slot=NULL 不受约束，
--    核销码可被后续新单复用。
-- 5. shift 含 NULL 列（weekday/date 随 scope 二选一），无法直接做条件唯一，
--    唯一性由应用层 upsert 保证。
-- 6. 软删除：service / service_category / technician 用 deleted 标记，查询默认过滤。
-- =====================================================================