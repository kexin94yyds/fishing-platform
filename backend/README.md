# 淡水垂钓基地运营管理平台后端

Java 17、Spring Boot 3.5.16、MyBatis、Flyway 和 Spring Security 构成的单体后端。

## 本地演示

默认使用内存 H2（MySQL 兼容模式），无需安装数据库。`demo` 配置会依次加载 `db/schema` 与 `db/demo`：

```bash
mvn spring-boot:run
```

运行前请用 `java -version` 确认 Maven 使用的是 JDK 17。

演示账号：

- 用户名：`admin`
- 密码：`admin123`

前端调用写接口前先请求 `GET /api/auth/csrf`，随后同时携带
`XSRF-TOKEN` Cookie 和 `X-XSRF-TOKEN` 请求头。登录成功会轮换 Session
和 CSRF 令牌，因此登录后应重新请求一次 `/api/auth/csrf`。

`GET /api/auth/registration` 会返回当前环境是否开放公开注册。演示环境开放，
`mysql` 环境默认关闭。开放时，注册接口为 `POST /api/auth/register`，请求体只需要：

```json
{
  "username": "operator_01",
  "displayName": "运营员",
  "password": "operator123"
}
```

用户名须为 4-32 位小写字母、数字或下划线，并以字母开头；密码须为
8-64 位、至少包含一个英文字母和一个数字，且不能含空格。新账号角色固定为
`OPERATOR`，注册成功后会自动登录。注册请求仍必须携带 CSRF Cookie 和请求头；
注册成功后也应重新请求一次 `/api/auth/csrf`。

`ADMIN` 可执行全部业务操作；`OPERATOR` 可处理预约、渔获、会员和销售单，
但分区、钓位、商品资料/库存、预约结单、到账确认、客流日台账和账号管理均由后端限制为管理员操作。
所有账号可调用 `POST /api/auth/change-password` 修改本人密码；管理员可通过
`/api/admin/accounts` 管理账号并查询 `/api/admin/account-audits`。角色、启停或密码变化会使旧会话在下一请求即时失效。

预约 `CONFIRMED` 仅可在垂钓日前取消；当日及历史预约由管理员调用
`POST /api/bookings/{id}/complete` 或 `/no-show` 结单。销售订单可通过
`GET /api/sales-orders/{id}` 回查商品明细。客流日汇总通过 `GET /api/traffic-daily`
查询，管理员使用 `PUT /api/traffic-daily/{date}` 按版本录入或更新。

## MySQL 8

数据库地址与凭据只通过环境变量提供：

```bash
export FISHING_DB_URL='jdbc:mysql://127.0.0.1:3306/fishing_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export FISHING_DB_USERNAME='fishing_app'
export FISHING_DB_PASSWORD='replace-me'
export FISHING_BOOTSTRAP_ADMIN_USERNAME='admin'
export FISHING_BOOTSTRAP_ADMIN_DISPLAY_NAME='系统管理员'
export FISHING_BOOTSTRAP_ADMIN_PASSWORD='replace-with-12-plus-characters1'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

`mysql` 配置只加载 `db/schema` 与 `db/mysql`，因此新数据库不会产生演示业务数据或固定管理员。数据库中没有启用的管理员时，必须提供上述初始化变量，密码须为 12–64 位、含英文字母与数字且无空格；已有启用管理员时不会重置账号。连接池同时把 MySQL 会话时区固定为 `+08:00`。

为保持历史上已执行 V2、V4 演示迁移的 MySQL 可继续前向迁移，Flyway 不改写旧迁移，MySQL 专属 V6 会在应用可服务前停用旧演示口令。启动守卫仅接受脚本、描述、SQL 类型和 checksum 均匹配原始 V2、V4 的缺失成功迁移；其他缺失、未来或失败状态都会阻止启动，避免宽泛兼容规则掩盖异常迁移历史。
`mysql` 配置默认设置 `fishing.registration.enabled=false`；如确需覆盖，可设置环境变量 `FISHING_REGISTRATION_ENABLED=true`。

## 验证

```bash
mvn test
```

Java 17 自动化套件共 83 项，覆盖 schema/demo 与 schema/mysql 迁移分层、历史 V2/V4 MySQL 前向迁移与异常迁移历史负控，以及登录、角色授权与注册开关、Session 固定攻击防护、登录后 CSRF、重复预订、
并发抢占、非标准时段拦截、钓位容量缩减、钓区停用、关闭钓位的历史库存绕过、
渔获关联预订的权威字段、读模型与 404、商品版本冲突、销售库存不足回滚、订单取消回补、
收款确认/取消互斥、账号生命周期、客流日汇总、MySQL 演示口令废止与安全管理员初始化，以及统计响应的一致性。主要自动化数据库为 H2 MySQL 兼容模式；本轮另在 MySQL 8.4.11 临时新库与历史 V1–V5 库完成迁移、登录和关键接口验收，临时库已清理。正式部署仍应在目标数据库或 CI/Testcontainers 中重复验证。
