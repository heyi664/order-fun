# Load-test users and login tokens

Generate one isolated test user and Redis login token per k6 VU:

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
