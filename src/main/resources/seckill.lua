local voucherId = ARGV[1]
local userId = ARGV[2]
local quantity = tonumber(ARGV[4])
local perOrderLimit = tonumber(ARGV[5])
local perUserLimit = tonumber(ARGV[6])
local expireAt = tonumber(ARGV[7])

local stockKey = 'seckill:stock:' .. voucherId
local userCountKey = 'seckill:user-count:' .. voucherId
local stock = tonumber(redis.call('get', stockKey) or '0')
if quantity == nil or quantity <= 0 or quantity > perOrderLimit then
    return 3
end
if stock < quantity then
    return 1
end

local bought = tonumber(redis.call('hget', userCountKey, userId) or '0')
if bought + quantity > perUserLimit then
    return 2
end

redis.call('decrby', stockKey, quantity)
redis.call('hincrby', userCountKey, userId, quantity)
if expireAt ~= nil and expireAt > 0 then
    redis.call('expireat', userCountKey, expireAt)
end
return 0
