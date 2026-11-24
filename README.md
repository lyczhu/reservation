# 轻量美业预约小程序

面向单店美业的预约系统：用户端微信小程序、商家端微信小程序，以及 Spring Boot 后端。

## 技术栈

| 部分 | 选型 |
|---|---|
| 后端 | JDK 25、Spring Boot 4.1.2、MyBatis-Plus、Sa-Token、Flyway |
| 存储 | MySQL 8.0、Redis 7、RustFS（S3 协议，AWS SDK v2 访问） |
| 前端 | uni-app（Vue 3 + TypeScript）、`@wot-ui/ui`、pinia、pnpm workspace |
| 构建与部署 | Maven、Vite、pnpm、Docker Compose |

## 目录结构

```
server/               Spring Boot 后端（Maven 单模块）
  src/main/java/com/lawyus/reservation/
    common/           统一响应与错误码、Sa-Token 与密钥、审计
    kernel/           预约内核（行业无关）：时段、预约单与核销、订阅配额
    beauty/           美业皮肤：门店、服务、技师、排班、看板、账号、审计
    customer/         顾客端接口：登录与手机号、浏览、下单
    integration/      微信能力、对象存储、文件上传
    job/              到店提醒定时任务
  src/main/resources/db/migration/   Flyway 迁移脚本
frontend/             pnpm workspace：apps/user、apps/merchant、packages/shared
brands/               品牌配置、生成脚本与切换说明
deploy/               Dockerfile、docker-compose、bootstrap.sql
```

## 本地运行

前置：JDK 25、Maven 3.9+、Node ≥ 20、pnpm 10.30.3、Docker。

**1. 启动中间件**

```powershell
cd deploy
Copy-Item .env.example .env
docker compose up -d mysql redis rustfs
```

`.env.example` 内为本地开发默认值；生产环境必须替换其中的密钥。

**2. 启动后端**

```powershell
cd server
mvn spring-boot:run
```

- 默认 `dev` profile，Flyway 启动时自动建表；启动过程不写入业务数据（门店、服务、技师、排班均由商家端录入）。
- 未配置小程序 AppID 时，仅 `dev` 允许微信登录降级与内容安全检测跳过，其他环境直接报配置错误。
- 接口文档：<http://localhost:8080/swagger-ui/index.html>

**3. 运行小程序**

```powershell
cd frontend
pnpm install
pnpm dev:user       # 用户端
pnpm dev:merchant   # 商家端
```

开发产物在 `frontend/apps/<app>/dist/dev/mp-weixin`，用微信开发者工具导入；正式上传用 `pnpm build:user` / `pnpm build:merchant`，产物在 `dist/build/mp-weixin`。

- 小程序 AppID：`frontend/apps/*/src/manifest.json` 的 `mp-weixin.appid`
- 后端地址：`frontend/packages/shared/src/constants.ts` 的 `API_BASE_URL`（默认 `http://localhost:8080/api/v1`）；真机调试需改为局域网 IP 并在小程序后台配置合法域名
- 用户端手机号快捷验证需在小程序后台「用户隐私保护指引」中声明手机号用途，否则 `getPhoneNumber` 返回 errno 112

**4. 可选：H5 本地预览**

H5 仅用于本地界面预览与联调，微信登录、订阅、扫码走条件编译降级（需后端为 `dev` 且未配置 AppID）：

```powershell
pnpm --filter @reservation/user dev:h5
pnpm --filter @reservation/merchant dev:h5
```

**5. 单元测试**

```powershell
cd server
mvn test
```

## 构建与部署

```powershell
cd server
mvn -DskipTests package
cd ../deploy
docker compose up -d --build server
```

`deploy/Dockerfile` 基于 `eclipse-temurin:25-jre`，直接复制 `server/target/reservation-server-1.0.0.jar`，因此必须先完成 Maven 打包。

上线前在 `deploy/.env` 设置 `SPRING_PROFILES_ACTIVE=prod`，并执行 `deploy/bootstrap.sql` 初始化门店、预约规则与初始店长账号

## 配置入口

| 内容 | 位置 |
|---|---|
| 后端与中间件环境变量 | `deploy/.env.example` |
| 品牌名称、文案与法务文本 | `brands/README.md` |
| 预约规则默认值 | 建表迁移脚本 `V1__init.sql`，运行期在商家端「预约规则」页调整 |

## 许可证

Apache License Version 2.0
