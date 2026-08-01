param(
    [ValidateRange(1, 100000)]
    [int]$Count = 100,
    [long]$StartUserId = 900000001,
    [ValidateRange(60, 31536000)]
    [int]$TtlSeconds = 2160000,
    [string]$OutputDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$tokenFile = Join-Path $OutputDirectory 'k6.env'
$userSqlFile = Join-Path $OutputDirectory 'load-users.sql'
$redisFile = Join-Path $OutputDirectory 'load-redis.txt'
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()

function New-LoadTestToken {
    $bytes = New-Object byte[] 24
    $rng.GetBytes($bytes)
    return 'lt_' + ([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())
}

$tokens = New-Object System.Collections.Generic.List[string]
$userSql = New-Object System.Text.StringBuilder
$redisCommands = New-Object System.Text.StringBuilder

for ($slot = 1; $slot -le $Count; $slot++) {
    $userId = $StartUserId + $slot - 1
    $token = New-LoadTestToken
    $nickName = "loadtest_$slot"
    $email = "loadtest-$slot@example.test"

    [void]$tokens.Add($token)
    [void]$userSql.AppendLine("INSERT INTO tb_user (id, email, nick_name, icon, create_time, update_time) VALUES ($userId, '$email', '$nickName', '', NOW(), NOW()) ON DUPLICATE KEY UPDATE email = VALUES(email), nick_name = VALUES(nick_name), icon = VALUES(icon), update_time = NOW();")
    [void]$redisCommands.AppendLine(('HSET login:token:{0} id {1} nickName {2} icon ""' -f $token, $userId, $nickName))
    [void]$redisCommands.AppendLine("EXPIRE login:token:$token $TtlSeconds")
}

[System.IO.File]::WriteAllText($tokenFile, 'AUTH_TOKENS=' + ($tokens -join ',') + [Environment]::NewLine, $utf8NoBom)
[System.IO.File]::WriteAllText($userSqlFile, $userSql.ToString(), $utf8NoBom)
[System.IO.File]::WriteAllText($redisFile, $redisCommands.ToString(), $utf8NoBom)

$rng.Dispose()

Write-Host "Generated $Count test users and tokens in $OutputDirectory"
Write-Host "- k6.env"
Write-Host "- load-users.sql"
Write-Host "- load-redis.txt"
