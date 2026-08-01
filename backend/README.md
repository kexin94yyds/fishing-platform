# 淡水垂钓基地运营管理平台后端

Java 17、Spring Boot 3.5.16、MyBatis、Flyway 和 Spring Security 构成的单体后端。

## 本地演示

默认使用内存 H2（MySQL 兼容模式），无需安装数据库：

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

公开注册接口为 `POST /api/auth/register`，请求体只需要：

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

## MySQL 8

数据库地址与凭据只通过环境变量提供：

```bash
export FISHING_DB_URL='jdbc:mysql://127.0.0.1:3306/fishing_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export FISHING_DB_USERNAME='fishing_app'
export FISHING_DB_PASSWORD='replace-me'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Flyway 会自动创建表和演示数据。生产环境应在部署后立即修改或停用演示账号。

## 验证

```bash
mvn test
```

集成测试覆盖登录与授权、Session 固定攻击防护、登录后 CSRF、重复预订、
并发抢占、非标准时段拦截、钓位容量缩减、钓区停用、关闭钓位的历史库存绕过、
渔获关联预订的权威字段、读模型与 404、销售库存不足回滚，以及收款后业务单状态推进。
