# Python Agent 接口要求与项目现状

## 1. 当前项目现状

本项目当前采用“静态前端 + Java Spring Boot 后端 + Python Agent 微服务”的规划方式。

### 前端现状

- 前端目录：`frontend/nginx-1.18.0/html/hmdp`
- AI 聊天页：`chat.html`
- AI 聊天页样式：`css/chat.css`
- 页面形态：桌面网页版聊天工作台，不再按手机版设计。
- 浏览器访问地址：`http://localhost:8080/chat.html`
- 前端实际请求：

```js
axios.post("/ai/chat", {
  conversationId: this.conversationId,
  message: content,
  history: this.messages.slice(-10).map(m => ({
    role: m.role,
    content: m.content
  }))
})
```

由于 `common.js` 中配置了：

```js
axios.defaults.baseURL = "/api";
```

所以浏览器真实请求地址为：

```http
POST /api/ai/chat
```

Nginx 应将 `/api/*` 转发到 Java 后端 `http://127.0.0.1:8081/*`。

### Java 后端现状

- Java 服务端口：`8081`
- 已实现接口：

```http
POST /ai/chat
```

- Controller：`src/main/java/com/heyee/comments/controller/AiChatController.java`
- Service：`src/main/java/com/heyee/comments/service/impl/AiChatServiceImpl.java`
- Java 会将请求转发给 Python Agent：

```http
POST {AGENT_SERVICE_URL}/v1/agent/chat
```

默认配置：

```yaml
agent:
  service-url: ${AGENT_SERVICE_URL:http://127.0.0.1:8000}
  connect-timeout: ${AGENT_CONNECT_TIMEOUT:3000}
  read-timeout: ${AGENT_READ_TIMEOUT:30000}
```

`.env.example` 中已提供：

```env
AGENT_SERVICE_URL=http://127.0.0.1:8000
AGENT_CONNECT_TIMEOUT=3000
AGENT_READ_TIMEOUT=30000
```

### 登录要求

AI 聊天接口需要登录。

`/ai/chat` 没有加入 `MvcConfig` 的登录白名单，因此前端必须携带 `authorization` token。当前前端会从 `sessionStorage.token` 读取 token 并放入请求头。

## 2. 调用链路

完整链路如下：

```text
浏览器 chat.html
  -> POST /api/ai/chat
  -> Nginx 反向代理
  -> Java Spring Boot POST /ai/chat
  -> Python Agent POST /v1/agent/chat
  -> Java Result.ok(data)
  -> 前端展示 data.reply
```

Java 对前端返回统一 `Result` 格式：

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "conversationId": "conv_001",
    "reply": "这里是 AI 回复内容",
    "createdAt": "2026-06-17T22:30:00",
    "sources": [],
    "toolCalls": []
  },
  "total": null
}
```

Python Agent 只需要返回 `data` 内部对象，Java 会包装成 `Result.ok(...)`。

## 3. Python Agent 必须实现的接口

Python Agent 默认运行在：

```text
http://127.0.0.1:8000
```

推荐项目目录名：

```text
agent-service
```

推荐技术栈：

- FastAPI
- Uvicorn
- OpenAI-compatible SDK 或 HTTP 客户端
- Pydantic

### 3.1 健康检查接口

用于确认 Python Agent 是否启动成功。

```http
GET /health
```

成功响应：

```json
{
  "status": "ok",
  "service": "heyee-agent",
  "version": "0.1.0"
}
```

要求：

- HTTP 状态码返回 `200`
- 不依赖大模型 API 是否可用
- 只要 Python 服务进程正常，就应该返回成功

### 3.2 聊天接口

Java 后端会调用该接口。

```http
POST /v1/agent/chat
Content-Type: application/json
```

请求体：

```json
{
  "userId": 1,
  "conversationId": "conv_001",
  "message": "帮我推荐一家适合周末聚餐的店",
  "history": [
    {
      "role": "user",
      "content": "你好"
    },
    {
      "role": "assistant",
      "content": "你好，我是 HYEEE AI，有什么可以帮你？"
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | number/null | 否 | 当前登录用户 ID，由 Java 从登录态中传入 |
| conversationId | string/null | 否 | 会话 ID，首次聊天可能为空 |
| message | string | 是 | 用户本次输入 |
| history | array | 否 | 最近若干轮聊天历史 |
| history[].role | string | 是 | `user` 或 `assistant` |
| history[].content | string | 是 | 历史消息内容 |

成功响应：

```json
{
  "conversationId": "conv_001",
  "reply": "可以考虑选择评分较高、适合多人聚餐、距离较近的火锅或家常菜店。",
  "createdAt": "2026-06-17T22:30:00",
  "sources": [],
  "toolCalls": []
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| conversationId | string | 是 | 会话 ID。若请求为空，Python Agent 需要生成一个新的 |
| reply | string | 是 | 返回给用户展示的 AI 回复 |
| createdAt | string | 否 | ISO 8601 时间字符串。为空时 Java 会自动补当前时间 |
| sources | array | 否 | 信息来源，后续 RAG 或业务查询可使用 |
| toolCalls | array | 否 | 工具调用记录，后续调试和展示可使用 |

## 4. Python Agent 行为要求

### 基础要求

- `message` 为空时返回 `400`，并说明错误原因。
- `conversationId` 为空时生成新的会话 ID。
- `history` 为空或缺失时按新会话处理。
- `reply` 不允许为空字符串。
- 单次请求建议在 30 秒内返回，避免 Java 读取超时。

### 会话要求

第一阶段可以不做服务端持久化，只依赖前端传来的 `history` 生成上下文。

后续可以扩展：

- Redis 保存会话上下文
- MySQL 保存长期聊天记录
- 基于 `userId` 做用户个性化推荐

### 错误响应建议

当 Python Agent 发生错误时，建议返回明确 JSON：

```json
{
  "detail": "AI model is unavailable"
}
```

常见 HTTP 状态码：

| 状态码 | 场景 |
| --- | --- |
| 200 | 正常返回 |
| 400 | 请求参数错误 |
| 500 | Agent 内部异常 |
| 503 | 大模型服务不可用 |

Java 当前会把调用异常统一转换为：

```json
{
  "success": false,
  "errorMsg": "AI 服务暂时不可用，请确认 Python Agent 已启动",
  "data": null,
  "total": null
}
```

## 5. Python Agent 环境变量要求

建议 Python Agent 支持以下环境变量：

```env
AGENT_HOST=127.0.0.1
AGENT_PORT=8000
AI_API_KEY=your_ai_api_key
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
JAVA_SERVICE_URL=http://127.0.0.1:8081
```

说明：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| AGENT_HOST | 否 | Agent 监听地址，默认 `127.0.0.1` |
| AGENT_PORT | 否 | Agent 监听端口，默认 `8000` |
| AI_API_KEY | 是 | 大模型 API Key |
| AI_BASE_URL | 否 | OpenAI-compatible 接口地址 |
| AI_MODEL | 是 | 使用的模型名称 |
| JAVA_SERVICE_URL | 否 | 后续 Python Agent 调 Java 业务接口时使用 |

## 6. 最小可用版本验收标准

Python Agent 完成后，需要满足以下验收条件：

1. 启动 Python Agent 后，访问 `GET http://127.0.0.1:8000/health` 返回 `200`。
2. 直接请求 `POST http://127.0.0.1:8000/v1/agent/chat` 能返回非空 `reply`。
3. Java 后端启动后，登录前端账号并访问 `http://localhost:8080/chat.html`。
4. 在聊天页输入问题后，前端能展示用户消息和 AI 回复。
5. Python Agent 未启动时，前端能看到明确错误提示，而不是页面崩溃。

## 7. 后续扩展方向

第一阶段只需要打通聊天闭环。后续再逐步增加：

- 店铺查询工具
- 优惠券查询工具
- 评论总结工具
- 用户偏好记忆
- RAG 知识库
- Agent 工具调用记录展示
- 聊天记录持久化

