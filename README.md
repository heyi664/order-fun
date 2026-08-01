# HYEEE Agent Hub

HYEEE Agent Hub 是一个面向 Agent 使用者的社区与服务平台。用户可以交流 Agent 使用经验、围绕话题讨论、购买并兑换 Token 包，以及通过 RAG Agent 进行 AI 对话；管理员可以管理 Token 包的发布与限购规则。

## 功能概览

### 用户端

- **Agent 社区**：发布帖子、上传图片、浏览帖子、点赞、评论、收藏。
- **话题热榜**：帖子中使用 `#话题` 即可参与话题讨论，系统按热度展示排行榜。
- **Token 包**：查看限时 Token 包、库存、限购数量和倒计时；通过模拟支付确认后创建订单，再将订单兑换为 Token 余额。
- **AI 对话**：通过 Java 服务转发到 Python RAG Agent，支持流式回复。
- **账户能力**：邮箱验证码登录、退出登录、个人帖子、收藏列表和 Token 余额查看。
- **登录拦截**：发帖、AI 对话、点赞、收藏、评论等受限操作会跳转登录；登录成功后自动返回原位置并继续原操作。

### 管理端

- 独立的管理员账号与密码登录。
- 管理员注册校验码保护。
- 发布 Token 包，并设置价格、Token 数量、总库存、单次购买上限、每人累计上限、抢购起止时间。

## 技术组成

| 层级 | 主要技术 |
| --- | --- |
| 前端 | 原生 HTML / CSS / JavaScript、Nginx |
| Java 后端 | Spring Boot 2.3、MyBatis-Plus、Redis、RocketMQ、MySQL |
| AI 服务 | 独立 Python RAG / Agent 服务，通过 HTTP 调用 |
| 静态资源 | Nginx 提供 Agent Hub 页面与 `/imgs/` 帖子图片 |

## 目录说明

```text
src/main/java/                       Java 后端源码
src/main/resources/application.yaml  后端配置
src/main/resources/db/migrations/    数据库增量 SQL
frontend/nginx-1.18.0/               Nginx 与静态前端
frontend/nginx-1.18.0/html/hmdp/agent-platform/
                                    Agent Hub 页面源码
```

## 快速启动

### 1. 准备依赖服务

需要准备以下服务：

- MySQL
- Redis
- RocketMQ（Token 秒杀下单会使用）
- Python Agent 服务（AI 对话会使用）

数据库基础表与增量脚本位于 `src/main/resources`。请先导入基础 SQL，再按版本顺序执行 `db/migrations` 下的增量 SQL。

### 2. 配置后端

在 IDE 的 Spring Boot Run Configuration 或系统环境变量中配置：

```text
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/hycomment?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的数据库密码

REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=你的Redis密码

ROCKETMQ_NAME_SERVER=127.0.0.1:9876
AGENT_SERVICE_URL=http://127.0.0.1:8000

MAIL_HOST=smtp.163.com
MAIL_PORT=465
MAIL_USERNAME=你的发件邮箱
MAIL_PASSWORD=邮箱SMTP授权码
MAIL_FROM=你的发件邮箱
MAIL_SMTP_STARTTLS_ENABLE=false
MAIL_SMTP_SSL_ENABLE=true

ADMIN_BOOTSTRAP_REGISTER_CODE=首次管理员注册校验码
```

> 邮箱登录不限制用户邮箱服务商；SMTP 配置只决定系统用哪个邮箱发送验证码。

### 3. 启动 Java 后端

推荐使用 JDK 11，在项目根目录执行：

```powershell
mvn clean spring-boot:run
```

后端默认运行在 `http://127.0.0.1:8081`。

如果 PowerShell 找不到 `mvn`，请先安装 Maven 并将其 `bin` 目录加入 `PATH`，或在 IntelliJ IDEA 中直接启动 `HeyeeCommentsApplication`。

### 4. 启动前端 Nginx

在另一个 PowerShell 窗口执行：

```powershell
cd E:\JavaProject\heyee-comments\frontend\nginx-1.18.0
.\nginx.exe -p . -c conf\nginx.conf
```

打开：<http://127.0.0.1:8080/>

Nginx 会将 `/api/*` 请求转发到 Java 后端 `http://127.0.0.1:8081/*`。

### 5. 停止 Nginx

```powershell
cd E:\JavaProject\heyee-comments\frontend\nginx-1.18.0
.\nginx.exe -s quit -p . -c conf\nginx.conf
```

## 使用流程

1. 用户打开 Agent Hub，使用邮箱验证码登录。
2. 登录后可发布带图片和 `#话题` 的帖子，并对其他帖子点赞、收藏、评论。
3. 在 Token 包页面通过资格检查后进入模拟支付页；点击“确认支付”才会创建订单并扣减库存。
4. 在“我的 Token 包订单”中兑换订单，Token 会计入用户余额。
5. 在 AI 对话页调用已配置的 Python RAG Agent。

## 注意事项

- Token 包抢购依赖 Redis 库存、用户限购记录与 RocketMQ 异步下单；RocketMQ 不可用时不要反复点击抢购。
- Token 包订单号以字符串形式返回前端，避免 JavaScript 对长整型精度丢失。
- 当前支付页为模拟支付，不接入真实支付渠道。
- AI 对话暂未实现按 Token 计费和扣减余额。
- Nginx 入口配置在 `frontend/nginx-1.18.0/conf/nginx.conf`；页面资源在 `agent-platform` 目录，帖子图片继续从 `/imgs/` 提供。

## 常用地址

| 地址 | 说明 |
| --- | --- |
| `http://127.0.0.1:8080/` | Agent Hub 用户端 |
| `http://127.0.0.1:8080/login.html` | 用户 / 管理员登录 |
| `http://127.0.0.1:8080/admin.html` | Token 包管理端（需管理员登录） |
| `http://127.0.0.1:8081/actuator` | Spring Boot Actuator（后端已启动时） |
