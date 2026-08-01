# 淡水垂钓基地运营管理平台

面向垂钓基地日常运营与毕业设计答辩的前后端分离管理平台。管理端采用“湖畔运营所”视觉与信息架构，围绕现场值守、湖区状态和业务闭环组织页面。项目采用单体后端与独立管理端，不引入微服务、真实支付网关或复杂审批流，重点保证业务闭环、数据一致性和可演示性。

## 技术栈

- 后端：Java 17、Spring Boot 3.5.16、MyBatis 3.0.4、Spring Security、Flyway
- 数据库：MySQL 8；默认演示环境使用 H2 的 MySQL 兼容模式
- 前端：Vue 3、TypeScript、Vite、Element Plus、ECharts、Pinia、Axios

## 核心功能

1. 账号与权限：演示环境支持运营人员自助注册并自动登录，MySQL 环境默认关闭公开注册；管理员与运营人员按业务能力分权
2. 钓区与钓位管理：维护分区、钓位、容量、开放状态和语义地图坐标
3. 时段预订：查询余量、创建和取消预订，以事务和条件更新防止超额预订
4. 渔获登记：维护品种、重量、数量、会员及关联预订
5. 渔具售卖：维护商品库存并创建现场销售单，库存不足时整单回滚
6. 会员管理：维护会员编号、联系方式、等级、积分和状态
7. 收费订单：查看销售单待收款记录并人工确认，联动销售单状态
8. 客流分析：展示客流、访客、会员、预订和收入趋势；预约、会员和营收均从业务事实表实时聚合

权限边界如下：运营人员可处理预约、渔获、会员和现场销售，但不能维护分区钓位、商品资料与库存，也不能确认到账；这些管理操作仅管理员可执行。前端会隐藏无权入口，后端同时强制校验角色。

## 体验与可访问性

管理端统一提供骨架加载、失败重试、业务空状态与表单错误聚焦；全局壳层采用语义化导航和“跳到主要内容”入口，并支持 Cmd/Ctrl+K 搜索八个模块、方向键/回车/Escape 操作、路由切换焦点管理及 reduced-motion。桌面端与 390px 移动端均可操作。

## 本地启动

### 1. 启动后端

需要 JDK 17 和 Maven 3.9 或更高版本；运行前请用 `java -version` 确认 Maven 使用的是 JDK 17。

```bash
cd backend
mvn spring-boot:run
```

默认启用 `demo` 配置，使用内存数据库并自动执行 Flyway 迁移和演示数据初始化，服务地址为 `http://127.0.0.1:18080`。如需改端口，可设置 `FISHING_SERVER_PORT`。

### 2. 启动前端

需要 Node.js 20 或更高版本。

```bash
cd frontend
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`，演示账号：

- 用户名：`admin`
- 密码：`admin123`

演示环境的登录页可进入独立注册页。新账号必须使用 4–32 位用户名和包含英文字母、数字的
8–64 位密码；注册成功后会以“运营人员”身份自动进入“湖畔运营所”。

前端开发服务器会把 `/api` 请求代理到后端的 18080 端口。若后端使用其他端口，可在启动前设置 `VITE_API_PROXY_TARGET`。

## 使用 MySQL 8

先创建空数据库，再通过环境变量启动 `mysql` 配置。Flyway 会自动建表并写入演示数据。

```bash
cd backend
export FISHING_DB_URL='jdbc:mysql://127.0.0.1:3306/fishing_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export FISHING_DB_USERNAME='root'
export FISHING_DB_PASSWORD='your-password'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

数据库账号与密码不写入项目文件。

`mysql` 配置默认关闭公开注册。部署方如确需开放，应显式设置
`FISHING_REGISTRATION_ENABLED=true`，并先评估账号审批与人员管理流程。

## 验证

```bash
cd backend
mvn test

cd ../frontend
npm run lint
npm run typecheck
npm run build
```

当前 Java 17 + H2 MySQL 兼容模式共有 22 项后端集成测试。MySQL 8 真环境尚未纳入自动化验证；本机验证曾受 Homebrew MySQL 动态库损坏阻断，因此部署前仍应在可用的 MySQL 8 或 CI/Testcontainers 环境执行迁移与接口回归。

## 目录结构

```text
backend/    Spring Boot API、业务服务、MyBatis Mapper、Flyway 迁移与集成测试
frontend/   Vue 管理端、页面组件、接口适配、状态管理与 ECharts 图表
```

首版支付确认闭环只覆盖现场商品销售。预约金额是运营人员创建预约时填写的业务记录字段，不接入在线支付。
