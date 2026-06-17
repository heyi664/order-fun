# Python Agent 智能客服模块总体规划

## 1. 可行性结论

可以实现。

推荐采用“Java 主项目 + Python Agent 微服务”的架构：

- 现有 Spring Boot 项目继续负责用户、店铺、优惠券、评论、登录态等业务能力。
- 新增 Python 服务负责智能客服对话、工具调用、知识检索。
- 后期 harness 评测模块同样放在 Python 服务中开发。
- Java 侧通过 REST 接口调用 Python agent，并继续使用现有 `Result` 返回格式。

## 2. 推荐架构

```text
前端
  |
  v
Spring Boot 后端
  |-- 用户登录 / 店铺 / 优惠券 / 评论 / 秒杀等原有能力
  |
  | REST 调用
  v
Python Agent Service
  |-- 智能客服对话
  |-- 工具调用
  |-- 会话上下文
  |-- 后期 Harness 评测
```

## 3. 技术选型

| 模块 | 推荐技术 |
|---|---|
| Python Web 服务 | FastAPI |
| 数据校验 | Pydantic |
| Agent 编排 | LangChain / OpenAI SDK / 自定义轻量编排 |
| LLM 接口 | OpenAI-compatible API |
| 测试框架 | pytest |
| Harness | Python 独立评测脚本 + 测试数据集 |
| Java 调用方式 | RestTemplate / WebClient |
| 配置管理 | `.env` + Spring `application.yaml` |

## 4. 阶段计划表

| 阶段 | 目标 | 主要工作 | 产出 |
|---|---|---|---|
| 1. 环境与边界确认 | 让现有 Java 服务稳定可启动 | 修复 Redis 配置不可达问题；确认 MySQL `hmdp` 数据可用；固定 JDK 8/Maven 启动方式 | 可运行的 Java 后端 |
| 2. Python Agent 服务骨架 | 建立独立 Python 模块 | 新建 `agent-service`；提供 `/health`、`/v1/agent/chat`；配置模型 key 和 Java 地址 | Python 服务可独立启动 |
| 3. Java 接入层 | 让现有系统能调用 agent | 新增 `/agent/chat`；获取当前用户；调用 Python agent；包装为 `Result` | 前端可通过 Java 访问 agent |
| 4. Agent 核心能力 | 实现智能客服闭环 | 定义系统提示词；支持多轮会话；接入店铺、优惠券、评论等工具 | 可回答业务问题的客服 agent |
| 5. 记忆与知识库 | 提升上下文能力 | 保存会话记录；整理业务知识；后续可接入向量库/RAG | 支持追问和业务知识问答 |
| 6. Harness 预留与评测 | 为后期质量评估铺路 | 建立 `harness/`；定义测试集；评估回答质量和工具调用准确性 | 可自动回归测试 agent |
| 7. 联调与验收 | 验证端到端体验 | Java + Python 双服务启动；测试登录态、超时、异常场景 | 可演示的智能客服功能 |

## 5. 接口规划

### Java 对外接口

```http
POST /agent/chat
```

请求示例：

```json
{
  "sessionId": "session-001",
  "message": "附近有什么推荐的美食店？"
}
```

响应继续使用现有项目的 `Result` 格式：

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "answer": "为你推荐几家美食店...",
    "sessionId": "session-001",
    "sources": [],
    "toolCalls": []
  },
  "total": null
}
```

### Python Agent 内部接口

```http
GET /health
POST /v1/agent/chat
```

Python 返回结构建议：

```json
{
  "answer": "客服回复内容",
  "sessionId": "session-001",
  "sources": [
    {
      "type": "shop",
      "id": 1,
      "name": "103茶餐厅"
    }
  ],
  "toolCalls": [
    {
      "name": "search_shops",
      "arguments": {
        "keyword": "美食"
      },
      "success": true
    }
  ]
}
```

## 6. Agent 能力规划

第一版建议支持：

- 平台使用说明问答
- 店铺推荐
- 店铺分类查询
- 优惠券查询
- 热门笔记/评论摘要
- 登录用户相关的个性化问答
- 无法回答时给出友好兜底回复

后续增强：

- RAG 知识库
- 用户历史偏好记忆
- 多工具自动规划
- 客服工单生成
- 安全策略与敏感问题拒答
- Harness 自动评测

## 7. Harness 规划

建议目录结构：

```text
agent-service/
  app/
    main.py
    api/
    agent/
    tools/
    schemas/
    config/
  harness/
    datasets/
      customer_service_cases.jsonl
      tool_call_cases.jsonl
    evaluators/
      answer_quality.py
      tool_accuracy.py
    run_eval.py
  tests/
  requirements.txt
  .env.example
```

Harness 评测维度：

| 维度 | 说明 |
|---|---|
| 回答准确性 | 是否回答用户真实问题 |
| 工具调用准确性 | 是否调用了正确工具 |
| 业务一致性 | 是否和店铺、优惠券、评论数据一致 |
| 多轮上下文 | 是否理解连续追问 |
| 异常处理 | 模型失败、工具失败时是否能降级 |
| 安全性 | 是否避免越权、泄露隐私或编造敏感信息 |

## 8. 测试计划

### Java 侧测试

- `/agent/chat` 未登录时应被现有登录拦截器拦截。
- 已登录时能正常转发请求到 Python agent。
- Python 服务不可用时返回友好错误。
- Python 服务超时时不会拖垮 Java 主服务。

### Python 侧测试

- `/health` 正常返回。
- 普通问答能返回客服回复。
- 店铺问题能触发店铺查询工具。
- 优惠券问题能触发优惠券查询工具。
- 模型 API 失败时能返回降级话术。

### Harness 测试

- 固定问答集回归测试。
- 工具调用准确率测试。
- 多轮对话测试。
- 异常输入测试。
- 越权问题测试。

## 9. 前置注意事项

当前项目启动前需要先解决 Redis 不可达问题：

```yaml
redis:
  host: 192.168.150.101
  port: 6379
  password: 123321
```

如果 Redis 不可访问，Spring Boot 会因为 `RedissonClient` 初始化失败而启动失败。

另外项目建议使用 JDK 8 启动：

```powershell
$env:JAVA_HOME='E:\javajdk1.8'
```

## 10. 默认假设

- Python 使用 3.11+。
- 第一版 agent 不直接连接前端，只通过 Java 后端暴露接口。
- 第一版不强制引入向量数据库。
- Java 继续负责用户鉴权。
- Python agent 通过 Java API 或数据库查询获取业务数据。
- Harness 后期独立演进，不影响主业务启动。
