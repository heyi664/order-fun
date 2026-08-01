# HYEEE Agent Hub 压测（k6）

脚本 [hy-agent-hub.js](hy-agent-hub.js) 按前端实际调用的接口覆盖社区首页、热门话题、Token 包、帖子详情和评论读取；提供令牌后会额外压测登录用户的账户与订单查询。默认没有任何写入操作，适合预发布环境的常规容量测试。

## 准备

1. 安装 [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)。
2. 确认预发布环境的 Java 服务、MySQL、Redis、RocketMQ 均可用，并使用 Nginx 入口或直接后端入口。
3. 将下方命令中的地址替换为预发布地址。通过 Nginx 时包含 `/api`；直连 Spring Boot 时不包含 `/api`。

不要将真实用户令牌提交到仓库。令牌应来自专用压测账号，且数量至少等于并发 VU 数，避免多个 VU 共享限流额度而影响结果。

## 常规读压测

PowerShell：

```powershell
$env:BASE_URL = 'https://staging.example.com/api'
$env:PROFILE = 'load'
$env:VUS = '20'
k6 run .\loadtest\k6\hy-agent-hub.js
```

预置档位：

| PROFILE | 行为 | 默认值 |
| --- | --- | --- |
| `smoke` | 单 VU、固定迭代，验证部署后接口可用性 | 10 次 |
| `load` | 线性升至稳定并发再降载 | 20 VU，1 分钟升载 + 3 分钟保持 |
| `stress` | 升至峰值并发，寻找容量拐点 | 50 VU，峰值 100 VU |
| `soak` | 固定并发，观察长时间资源稳定性 | 10 VU，30 分钟 |

例如先做冒烟：

```powershell
$env:BASE_URL = 'http://127.0.0.1:8080/api'
$env:PROFILE = 'smoke'
k6 run .\loadtest\k6\hy-agent-hub.js
```

## 带登录态的读取链路

接口使用原始 token 作为 `authorization` 请求头值（不加 `Bearer`）。令牌用英文逗号分隔：

```powershell
$env:BASE_URL = 'https://staging.example.com/api'
$env:PROFILE = 'load'
$env:VUS = '20'
$env:AUTH_TOKENS = 'token-of-test-user-1,token-of-test-user-2,token-of-test-user-3'
k6 run .\loadtest\k6\hy-agent-hub.js
```

此模式附加 `GET /user/me`、`GET /token-account`、`GET /token-account/orders`。仍然不执行点赞、评论、兑换或下单。

## Token 包秒杀压测（有副作用）

秒杀会创建订单、扣减 Redis 库存，并向 RocketMQ 投递异步订单消息。只可针对隔离的预发布 Token 包和压测账号执行，执行前应记录 Token 包 ID、初始库存和每用户限购值；执行后核对数据库订单数、库存与 Redis 状态。

```powershell
$env:BASE_URL = 'https://staging.example.com/api'
$env:PROFILE = 'smoke'
$env:VUS = '10'
$env:AUTH_TOKENS = 'token-1,token-2,token-3,token-4,token-5,token-6,token-7,token-8,token-9,token-10'
$env:ENABLE_SECKILL = 'true'
$env:VOUCHER_ID = '123'
$env:SECKILL_QUANTITY = '1'
k6 run .\loadtest\k6\hy-agent-hub.js
```

该接口有“单用户 10 秒最多 5 次”的限流。每轮会先调用资格校验，再使用唯一 `paymentRequestId` 下单。脚本单独输出 `seckill_accepted` 和 `seckill_rejected`；库存售罄、限购等业务拒绝必须结合服务端日志判断，不能简单当作 HTTP 故障。

### 多用户同一时刻下单

使用 [token-pack-seckill.js](token-pack-seckill.js) 做严格的并发抢购验证。它让每个 VU 固定使用一个不同的测试账号，并等待 `START_AT` 后仅提交一次下单请求；因此可模拟 N 个用户在同一秒抢同一个 Token 包。`VUS` 不得大于令牌数量。

当 token 数量很多（尤其 Windows 环境）时，不要把全部 token 放入 `AUTH_TOKENS` 环境变量。将生成的 `k6.env` 文件放在脚本目录，并设置 `AUTH_TOKENS_FILE=./k6.env`；文件可为一行 `AUTH_TOKENS=token1,token2,...`，也可为纯逗号分隔 token。

在启动命令前将开始时间设置为至少 30 秒后的未来时间，给所有 VU 足够的初始化时间：

```powershell
$env:BASE_URL = 'https://staging.example.com/api'
$env:VOUCHER_ID = '123'
$env:VUS = '20'
$env:SECKILL_QUANTITY = '1'
$env:AUTH_TOKENS = 'token-1,token-2,token-3,...,token-20'
$env:START_AT = '2026-07-31T22:00:00+08:00'
k6 run .\loadtest\k6\token-pack-seckill.js
```

压测前应使 Token 包处于活动时间窗内，并设置库存来覆盖目标场景：库存等于 VU 数验证全量成功；库存小于 VU 数验证超卖保护；每用户限购小于请求数量验证限购。完成后核对 `seckill_order_accepted`、`seckill_order_rejected`、数据库订单数、最终库存、Redis 库存以及 RocketMQ 消费结果是否一致。

## 判定与结果保存

脚本内置门槛：HTTP 失败率低于 1%，P95 低于 800ms，P99 低于 1500ms，业务成功率高于 99%。这些是起始门槛，应按上线 SLO、机器规格和压测环境网络条件调整。

将原始汇总保存为 JSON：

```powershell
New-Item -ItemType Directory -Force .\loadtest\results | Out-Null
k6 run --summary-export .\loadtest\results\summary.json .\loadtest\k6\hy-agent-hub.js
```

压测期间同时关注 Spring Boot Actuator、JVM GC/堆、MySQL 连接池与慢查询、Redis CPU/命中率、RocketMQ 堆积和 Nginx 5xx。AI 对话依赖 Python Agent，因响应模型与流式耗时差异较大，建议单独按 Agent 服务容量制定压测方案，避免与社区读链路混在同一结论里。
