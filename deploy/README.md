# Docker 部署

本目录用于在一台 Linux 服务器上部署 Java 后端、MySQL、Redis、RocketMQ 和 Nginx。
Python Agent 是独立项目，尚未包含在本 Compose 文件中；在其加入同一个 Docker 网络后，将
`AGENT_SERVICE_URL` 配置为 `http://agent:8000`。

## 首次启动

在仓库根目录执行：

```bash
cd /root/order-fun/deploy
cp .env.example .env
chmod 600 .env
vi .env
docker compose --env-file .env config --quiet
docker compose up -d --build
docker compose ps
```

`.env` 中所有 `CHANGE_ME` 值必须替换为随机的私密值。例如可用
`openssl rand -hex 32` 生成密码或令牌。不要把 `.env` 提交到 Git。

Compose 会把 MySQL、JDBC 和 Java 统一配置为 `Asia/Shanghai`。管理端发布 Token 包时，应按
北京时间填写开始和结束时间。

默认日志级别为 `INFO`，不会输出每条 SQL 的 DEBUG 日志。短时排障时可在 `.env` 设置
`LOG_LEVEL_COM_HEYEE=DEBUG`，修改后重建 `app` 容器；压测和日常运行应保持 `INFO`。

首次创建 `mysql-data` 卷时，MySQL 会自动导入 `deploy/mysql/schema.sql`。这是一份部署专用的
完整 schema，覆盖当前 Java 实体所需的全部表与字段。新库**不要**再运行 `db/migrations` 下的脚本；
增量脚本只用于升级历史数据库，并且应根据该数据库已有的版本选择性执行。

## 验证与日志

```bash
cd /root/order-fun/deploy
docker compose ps
docker compose logs -f app
docker compose logs -f broker
curl -i http://127.0.0.1/api/voucher/token-packs
```

## 秒杀链路指标

秒杀下单接口会记录以下 Actuator Timer：

- `seckill.order.request.duration`：整个下单接口。
- `seckill.voucher.query.duration`：券信息的 MySQL 查询。
- `seckill.redis.lua.duration`：Redis Lua 库存与限购扣减。
- `seckill.rocketmq.sync-send.duration`：等待 RocketMQ Broker ACK。

指标不通过 Nginx 对公网暴露。登录服务器后从应用容器读取，例如：

```bash
docker compose exec app sh -c 'wget -qO- http://127.0.0.1:8081/actuator/metrics/seckill.rocketmq.sync-send.duration'
```

浏览器访问 `http://服务器公网IP/`。仅在云安全组开放 TCP 80（以及管理用 SSH 22）；不要公开
MySQL 3306、Redis 6379、RocketMQ 9876/10911、Java 8081 和 Dashboard 8082。

Dashboard 仅绑定在服务器本机 `127.0.0.1:8082`。如需访问，使用本地 SSH 隧道：

```bash
ssh -L 8082:127.0.0.1:8082 root@服务器公网IP
```

然后在本机浏览器打开 `http://127.0.0.1:8082`。

Redis 同样只绑定服务器本机 `127.0.0.1:6379`。本地调试时建立 SSH 隧道：

```bash
ssh -L 6379:127.0.0.1:6379 root@server-public-ip
```

随后在本机 Redis 客户端连接 `127.0.0.1:6379`，并使用 `.env` 内的 `REDIS_PASSWORD`。不要在
云安全组开放 6379。

MySQL 也只绑定服务器本机 `127.0.0.1:3306`。本地数据库客户端可使用：

```bash
ssh -L 3306:127.0.0.1:3306 root@server-public-ip
```

连接地址为 `127.0.0.1:3306`，数据库名为 `hycomment`，用户名为 `.env` 中的
`MYSQL_APP_USER`，密码为 `MYSQL_APP_PASSWORD`。不要在云安全组开放 3306。

## 常用操作

```bash
cd /root/order-fun/deploy
./start-services.sh
docker compose restart app
docker compose logs --tail=200 app
docker compose down
```

`start-services.sh` 用于服务器重启后恢复服务：它会启动 Docker（如尚未启动）并执行
`docker compose up -d`，不会重建镜像、导入数据库或删除任何数据。首次部署完成后可执行
`systemctl enable docker`，使 Docker 在系统开机时自动启动；本项目的长期运行容器使用
`restart: unless-stopped`，通常会随 Docker 自动恢复。

`docker compose down` 不会删除数据卷。只有明确要清空全部数据库、缓存和消息数据时才使用
`docker compose down -v`，该操作不可恢复。
