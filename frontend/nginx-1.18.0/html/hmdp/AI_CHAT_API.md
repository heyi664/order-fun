# HYEEE AI 聊天接口文档

本文档是 `chat.html` 前端页面使用的后端接口约定。当前任务只提供前端调用和接口文档，不要求后端已实现。

## 1. 发送聊天消息

**接口地址**

```http
POST /ai/chat
```

由于前端 `js/common.js` 中 `axios.defaults.baseURL = "/api"`，浏览器实际请求地址为：

```http
POST /api/ai/chat
```

如果 Nginx 已将 `/api` 反向代理到 Spring Boot，后端 Controller 只需要暴露 `/ai/chat`。

**认证方式**

前端会自动从 `sessionStorage.token` 读取登录 token，并通过请求头传给后端：

```http
authorization: <token>
```

如果接口允许游客使用，可以忽略该请求头；如果要求登录，未登录时建议返回 HTTP 401。

**请求体**

```json
{
  "conversationId": "c_202606081200001",
  "message": "帮我推荐一家适合周末聚餐的店",
  "history": [
    {
      "role": "user",
      "content": "我想找一家适合朋友聚餐的店"
    },
    {
      "role": "assistant",
      "content": "可以优先看评分、距离、人均和评论热度。"
    }
  ]
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| conversationId | string | 否 | 会话 ID。首次聊天可以为空，后端可生成新 ID 返回 |
| message | string | 是 | 用户本次发送的文本，前端已做非空校验 |
| history | array | 否 | 最近 10 条上下文消息，按时间升序排列 |
| history[].role | string | 是 | `user` 或 `assistant` |
| history[].content | string | 是 | 历史消息内容 |

**成功响应**

需要符合项目现有 `Result` 响应结构：

```json
{
  "success": true,
  "data": {
    "conversationId": "c_202606081200001",
    "reply": "周末聚餐建议优先选择评分高、评论多、营业时间覆盖晚餐时段的店。你可以告诉我人数、预算和口味，我再帮你缩小范围。",
    "createdAt": "2026-06-08T12:00:00+08:00"
  }
}
```

**成功响应字段说明**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| success | boolean | 是 | 固定为 `true` |
| data.conversationId | string | 是 | 当前会话 ID。前端会保存到 `sessionStorage` |
| data.reply | string | 是 | AI 返回给用户的文本内容 |
| data.createdAt | string | 否 | AI 回复时间，ISO 8601 格式 |

**失败响应**

```json
{
  "success": false,
  "errorMsg": "消息内容不能为空"
}
```

**建议错误码**

| HTTP 状态 | 场景 | 前端表现 |
|---|---|---|
| 400 | `message` 为空或过长 | 聊天区显示错误提示 |
| 401 | 未登录或 token 失效 | 项目公共拦截器会跳转登录页 |
| 429 | 请求过快或超出限制 | 聊天区显示错误提示 |
| 500 | AI 服务异常 | 聊天区显示错误提示 |

## 2. 前端调用示例

`chat.html` 中的实际调用逻辑如下：

```js
axios.post("/ai/chat", {
  conversationId: this.conversationId,
  message: content,
  history: this.messages.slice(-10).map(m => ({
    role: m.role,
    content: m.content
  }))
}).then(({data}) => {
  this.conversationId = data.conversationId;
  this.messages.push({
    role: "assistant",
    content: data.reply,
    time: this.nowTime()
  });
});
```

## 3. 后端实现建议

后端可新增 Controller，例如：

```java
@PostMapping("/ai/chat")
public Result chat(@RequestBody AiChatRequest request) {
    // 校验 message
    // 调用真实 AI 或本地规则服务
    // 返回 conversationId 和 reply
    return Result.ok(response);
}
```

建议限制 `message` 长度，例如 1 到 1000 个字符，并对 AI 服务调用设置超时。