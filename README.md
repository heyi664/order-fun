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
