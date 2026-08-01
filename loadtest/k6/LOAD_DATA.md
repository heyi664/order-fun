# Load-test users and login tokens

Generate one isolated test user and Redis login token per k6 request:

```powershell
Set-Location E:\JavaProject\heyee-comments\loadtest\k6
.\generate-loadtest-data.ps1 -Count 100
```

The command generates three ignored, secret-bearing files:

- `k6.env`: tokens consumed by `token-pack-seckill.js`.
- `load-users.sql`: users to import into the target MySQL database.
- `load-redis.txt`: Redis commands to create the matching login sessions.

Copy the latter two files to the target server, then import them from its Compose directory:

```bash
docker compose exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < /root/order-fun/load-users.sql
docker compose exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --pipe' < /root/order-fun/load-redis.txt
```

Do not commit, paste, or share the generated files. `VUS` must not exceed the count used for generation.

## Staged QPS test

`token-pack-seckill-qps.js` measures sustained request rate, not a one-time simultaneous burst. It gives each request a distinct login token because the token package has a per-user purchase limit.

The default profile is `20:30s,40:30s,60:30s,80:30s,100:30s`: five 30-second plateaus, with a 10-second settling interval between them. It schedules about `9000` requests; generate `10000` test users/tokens and create a newly published token package with stock `10000` to leave a safe margin for stage-boundary scheduling.

```powershell
Set-Location E:\JavaProject\heyee-comments\loadtest\k6
.\generate-loadtest-data.ps1 -Count 10000
scp .\load-users.sql .\load-redis.txt root@111.231.23.186:/root/order-fun/
```

Import the generated files on the target server using the commands above. Then run locally after the new token package is already active:

```powershell
$env:BASE_URL='http://111.231.23.186/api'
$env:VOUCHER_ID='replace-with-new-voucher-id'
$env:AUTH_TOKENS_FILE='.\k6.env'
$env:RATE_PROFILE='20:30s,40:30s,60:30s,80:30s,100:30s'
$env:PRE_ALLOCATED_VUS='400'
$env:MAX_VUS='400'
k6 run --summary-export .\seckill-qps-staged.json .\token-pack-seckill-qps.js
```

`dropped_iterations` must remain `0`; otherwise the k6 injector did not have enough VUs to maintain the requested rate. Each `seckill_rps_*_duration_ms` metric gives the latency for that individual rate stage.
