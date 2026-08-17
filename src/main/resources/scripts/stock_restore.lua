-- KEYS: [1] stockKey, [2] userKey
-- ARGV: [1] quantity

local stockKey = KEYS[1]
local userKey = KEYS[2]
local quantity = tonumber(ARGV[1])

-- 사용자 구매 기록이 없다면 이미 복구됐거나 복구할 수 없는 상태
local userPurchased = redis.call('GET', userKey)

if not userPurchased then
    return 0
end

userPurchased = tonumber(userPurchased)

if userPurchased <= 0 then
    redis.call('DEL', userKey)
    return 0
end

-- 사용자 구매 수량보다 많은 재고가 복구되지 않도록 제한
local restoreQuantity = math.min(quantity, userPurchased)

-- 재고 복구
redis.call('INCRBY', stockKey, restoreQuantity)

-- 사용자 누적 구매 수량 차감
local remaining = redis.call(
        'DECRBY',
        userKey,
        restoreQuantity
)

-- 구매 수량이 모두 복구되면 사용자 Key 삭제
if remaining <= 0 then
    redis.call('DEL', userKey)
end

return restoreQuantity