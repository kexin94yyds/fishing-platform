# 淡水垂钓基地运营管理平台

面向钓友预约、垂钓基地日常运营与毕业设计答辩的前后端分离平台。钓友登录后进入“我的垂钓”单页，管理员和运营人员进入“湖畔运营所”现场工作台。项目采用单体后端与独立前端，不引入微服务、真实支付网关或复杂审批流，重点保证业务闭环、数据一致性和可演示性。

## 技术栈

- 后端：Java 17、Spring Boot 3.5.16、MyBatis 3.0.4、Spring Security、Flyway
- 数据库：MySQL 8；默认演示环境使用 H2 的 MySQL 兼容模式
- 前端：Vue 3、TypeScript、Vite、Element Plus、ECharts、Pinia、Axios

## 核心功能

1. 账号与权限：提供管理员、运营人员和钓友三类账号；管理员可创建、启停、调整工作人员角色、重置密码并查看审计，钓友与工作人员禁止跨域转换角色；所有账号可修改本人密码，角色、状态或密码变化会使旧会话即时失效
2. 钓区与钓位管理：维护分区、钓位、容量、基础收费、开放状态和语义地图坐标
3. 时段预订：管理员按开放日期维护时段容量、价格和状态；运营人员可登记会员或散客预约，钓友可在“我的垂钓”查询余量、按后端价格预约并查看/取消本人未来预约；同一账号或联系电话同一时段不得重复预订，取消、结单、爽约和收款均留存审计轨迹
4. 渔获登记：维护品种、重量、数量、日期、时段、钓区、会员及关联预订，支持组合筛选，并采用单向状态机和版本校验防止作废记录复活或陈旧覆盖
5. 渔具售卖：维护商品库存并创建现场销售单，库存不足时整单回滚；待收款订单可取消并精确回补库存
6. 会员管理：维护会员编号、联系方式、等级、积分和状态，集中查看会员预约、渔获、消费与会员操作审计
7. 收费订单：统一查看预约与销售待收款，人工确认到账；取消未收款预约会关闭对应收费单，取消销售单会回补库存
8. 客流分析：展示客流、访客、会员、预订、商品销售单/售出件数和收入趋势；管理员可维护带版本与审计信息的日客流台账，其他指标始终从业务事实表实时聚合

权限边界如下：钓友只能访问本人余量和预约接口，不能进入管理后台；运营人员可处理预约、渔获、会员和现场销售，但不能维护分区钓位、商品资料与库存，不能结单、确认到账、维护客流台账或管理账号；这些操作仅管理员可执行。前端会按角色跳转并隐藏无权入口，后端同时强制校验角色和数据所有权。

## 体验与可访问性

管理端统一提供骨架加载、失败重试、业务空状态与表单错误聚焦；全局壳层采用语义化导航和“跳到主要内容”入口，并支持 Cmd/Ctrl+K 搜索业务模块、方向键/回车/Escape 操作、路由切换焦点管理及 reduced-motion。桌面端与 390px 移动端均可操作。

## 本地启动

### 1. 启动后端

需要 JDK 17 和 Maven 3.9 或更高版本；运行前请用 `java -version` 确认 Maven 使用的是 JDK 17。

```bash
cd backend
mvn spring-boot:run
```

默认启用 `demo` 配置，使用内存数据库并自动执行 `db/schema` 和 `db/demo` 中的 Flyway 迁移及演示数据初始化，服务地址为 `http://127.0.0.1:18080`。如需改端口，可设置 `FISHING_SERVER_PORT`。

### 2. 启动前端

需要 Node.js 20 或更高版本。

```bash
cd frontend
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`，演示账号：

- 管理员：`admin` / `admin123`
- 钓友：`angler` / `angler123`

演示环境的登录页可进入独立钓友注册页。新账号必须使用 4–32 位用户名和包含英文字母、数字的
8–64 位密码；公开注册无论客户端传入什么角色都固定创建 `USER`，成功后自动进入“我的垂钓”。
管理员和运营人员账号只能由管理员在“账号管理”中创建。

前端开发服务器会把 `/api` 请求代理到后端的 18080 端口。若后端使用其他端口，可在启动前设置 `VITE_API_PROXY_TARGET`。

## Windows 环境安装与启动

Windows 10/11 可以直接运行本项目。毕设演示推荐使用默认 `demo` 配置：数据库位于后端进程内存中，无需安装 MySQL、Docker 或 WSL。

### 1. 环境安装清单

- JDK 17：从 [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17) 下载 Windows JDK 17 的 MSI 安装包。安装时启用 `Set JAVA_HOME` 和加入 `PATH`。
- Maven 3.9+：从 [Apache Maven](https://maven.apache.org/download.cgi) 下载 Binary zip，解压到固定目录，例如 `C:\Tools\apache-maven`；配置 `MAVEN_HOME`，并把 `%MAVEN_HOME%\bin` 加入用户 `Path`。
- Node.js：从 [Node.js 官网](https://nodejs.org/en/download) 安装当前 LTS 版本。项目要求 Node.js 20 或更高版本，安装包会同时安装 npm。
- Git：只有通过 Git 获取项目时才需要；直接复制或解压项目文件时可以不安装。

安装后关闭并重新打开 PowerShell，逐项检查：

```powershell
java -version
mvn.cmd -version
node --version
npm.cmd --version
```

`java -version` 和 `mvn.cmd -version` 中的 Java 都必须是 17。若电脑安装了多个 JDK，以 `mvn.cmd -version` 显示的版本为准。

建议把项目放在不含特殊权限限制的目录，例如 `D:\projects\fishing-platform`。项目支持中文路径，但答辩电脑使用简短英文路径更容易排查环境问题。

### 2. 双击一键启动

环境安装完成后，接收压缩包的用户只需要：

1. 完整解压项目，不要直接在压缩软件的预览窗口中运行。
2. 双击项目根目录的 `启动系统.bat`。
3. 首次运行等待 Maven 和 npm 下载依赖、完成构建。
4. 服务就绪后，脚本会自动打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)。

一键脚本会自动完成环境版本检查、前端依赖安装、后端打包、端口检查、前后端启动和健康检查。运行日志与进程编号保存在项目根目录的 `.runtime` 文件夹中；该文件夹不会进入 Git。
一键模式固定使用后端 18080、前端 5173；脚本会为子进程覆盖已有的 `FISHING_SERVER_PORT` 和 `VITE_API_PROXY_TARGET`，退出时恢复原值，避免历史环境变量导致健康检查地址与实际端口不一致。

重复双击启动脚本时，已运行的服务会被复用，不会重复启动。需要关闭系统时，双击项目根目录的 `停止系统.bat`。停止脚本只会终止由本项目记录且仍占用 18080/5173 端口的 Java、Node.js 进程，不会按进程名称批量结束其他程序。

首次运行需要联网下载依赖。依赖已经安装且 Maven 缓存完整后，后续启动可以不联网。若脚本检查失败，窗口会保留错误提示；详细运行输出可查看 `.runtime` 中的日志。

### 3. 手动启动后端

打开第一个 PowerShell，将示例路径替换为项目实际位置：

```powershell
Set-Location D:\projects\fishing-platform\backend
mvn.cmd spring-boot:run
```

首次启动会下载 Maven 依赖，需要保持网络畅通。看到 `Tomcat started on port 18080` 后，后端即启动成功。不要关闭这个窗口。

### 4. 手动启动前端

打开第二个 PowerShell：

```powershell
Set-Location D:\projects\fishing-platform\frontend
npm.cmd install
npm.cmd run dev
```

看到 `Local` 地址后在浏览器打开，通常为 [http://127.0.0.1:5173](http://127.0.0.1:5173)。如果端口被占用，Vite 会自动使用 5174、5175 等后续端口，请以终端实际输出为准。

演示账号：

- 管理员：`admin` / `admin123`
- 钓友：`angler` / `angler123`

默认演示数据库位于内存中，停止并重新启动后端后会恢复初始演示数据。两个服务均可在对应 PowerShell 中按 `Ctrl+C` 停止。

### 5. Windows 上执行验收

后端测试：

```powershell
Set-Location D:\projects\fishing-platform\backend
mvn.cmd test
```

前端检查：

```powershell
Set-Location D:\projects\fishing-platform\frontend
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
```

### 6. 常见问题

- `mvn.cmd` 找不到：检查 Maven 是否已解压，并确认 `%MAVEN_HOME%\bin` 已加入 `Path`；修改环境变量后需要重新打开 PowerShell。
- Maven 显示的 Java 不是 17：修正 `JAVA_HOME`，并确保 `%JAVA_HOME%\bin` 在旧 Java 路径之前。
- PowerShell 提示禁止运行 `npm.ps1`：直接使用文档中的 `npm.cmd`，无需更改系统执行策略。
- 前端提示网络连接失败：先确认后端窗口仍在运行，并检查 [http://127.0.0.1:18080/api/auth/registration](http://127.0.0.1:18080/api/auth/registration) 是否能返回 JSON。
- 双击启动脚本提示 5173 端口被占用：一键模式为了保证固定访问地址，不会自动改用其他端口；请先关闭占用 5173 端口的程序，再重新双击 `启动系统.bat`。
- 18080 端口被占用：使用 `netstat -ano | findstr :18080` 查找占用进程，关闭对应程序后再启动；也可以先设置 `$env:FISHING_SERVER_PORT=18081`，同时设置 `$env:VITE_API_PROXY_TARGET='http://127.0.0.1:18081'` 后分别启动后端和前端。
- Maven 首次下载依赖较慢：等待下载完成，不要反复中断；校园网或公司网络若拦截 Maven Central，需要切换到可正常访问的网络。
- 需要让同一局域网的其他设备访问：还需允许 Java 和 Node.js 通过 Windows 防火墙；仅在本机答辩展示时不需要开放防火墙。

## 使用 MySQL 8

先创建空数据库，再通过环境变量启动 `mysql` 配置。该配置只加载 `db/schema` 和 `db/mysql`：新 MySQL 不会写入演示业务数据，也不会创建固定管理员。若数据库中尚无启用的管理员，启动会要求部署方提供安全的初始管理员凭据；项目不提供 MySQL 默认密码。

为兼容历史上已经执行 V2、V4 演示迁移的 MySQL，Flyway 会保留这两条已应用记录并继续执行 MySQL 专属前向迁移，以停用旧演示口令。启动守卫仅允许脚本、描述、SQL 类型和 checksum 均匹配原始 V2、V4 的缺失成功迁移；其他缺失、未来或失败的迁移记录都会使启动失败，需先人工核查迁移历史。

```bash
cd backend
export FISHING_DB_URL='jdbc:mysql://127.0.0.1:3306/fishing_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export FISHING_DB_USERNAME='root'
export FISHING_DB_PASSWORD='your-password'
export FISHING_BOOTSTRAP_ADMIN_USERNAME='admin'
export FISHING_BOOTSTRAP_ADMIN_DISPLAY_NAME='系统管理员'
export FISHING_BOOTSTRAP_ADMIN_PASSWORD='replace-with-12-plus-characters1'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

初始管理员密码须为 12–64 位，至少包含一个英文字母和一个数字，且不能包含空格。数据库中已经存在启用的管理员时，后续启动可不再提供这三个初始化变量，现有账号也不会被重置或提权。数据库连接与管理员密码均不写入项目文件；MySQL 连接池会把每条连接的会话时区固定为 `+08:00`。

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

当前 Java 17 自动化套件共 89 项，覆盖 H2 MySQL 兼容模式、schema/demo 与 schema/mysql 迁移分层、历史 V2/V4 前向迁移、安全管理员初始化、迁移历史负控，以及钓友/工作人员权限隔离、本人数据所有权、服务端计价、跨入口重复预订防护、取消释放容量、散客信息、预订审计、时段配置、预约收费、渔获筛选、会员业务档案与审计、商品销售统计、账号、客流和并发一致性。本轮另在 MySQL 8.4.11 临时新库完成 V13 迁移、管理员创建 USER、钓友登录/预约/取消、后台越权拦截，以及带同手机号历史碰撞数据的 V12→V13 存量升级验收；临时库已清理。正式部署仍建议在目标 MySQL 版本或 CI/Testcontainers 中重复回归。

## 目录结构

```text
backend/    Spring Boot API、业务服务、MyBatis Mapper、Flyway 迁移与集成测试
frontend/   Vue 管理端、页面组件、接口适配、状态管理与 ECharts 图表
```

收费确认闭环覆盖垂钓预订与现场商品销售，均由管理员核实线下到账；系统不接入第三方支付、支付回调或线上退款。
