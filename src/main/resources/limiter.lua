local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

if not window or not limit or not now then
  return redis.error_reply("Invalid input parameters")
end

window = window * 1000
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local current = redis.call('ZCARD', key)
if current < limit then
  redis.call('ZADD', key, now, now .. '-' .. redis.call('INCR', key .. ':sequence'))
  redis.call('EXPIRE', key, window / 1000)
  redis.call('EXPIRE', key .. ':sequence', window / 1000)
  return current + 1
end

return 0
