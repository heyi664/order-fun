# 邮箱验证码登录配置

在启动后端的环境中设置下列环境变量（不要提交真实的 SMTP 授权码）：

```text
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your_sender@example.com
MAIL_PASSWORD=your_smtp_authorization_code
MAIL_FROM=your_sender@example.com
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_SMTP_SSL_ENABLE=false
```

Gmail 的 587 端口使用 STARTTLS（`MAIL_SMTP_STARTTLS_ENABLE=true`、`MAIL_SMTP_SSL_ENABLE=false`）。若使用 465 端口，则关闭 STARTTLS 并设置 `MAIL_SMTP_SSL_ENABLE=true`。每次调整变量后必须重启 Java 后端。

`MAIL_PASSWORD` 通常是邮箱服务商生成的 SMTP 授权码，而不是网页登录密码。`MAIL_FROM` 应使用该 SMTP 账号获准使用的发件地址。

已存在的数据库需在部署前执行 `src/main/resources/db/migrations/V2__add_email_login.sql` 一次；新建数据库直接导入 `src/main/resources/db/heyee-comments.sql` 即可。
