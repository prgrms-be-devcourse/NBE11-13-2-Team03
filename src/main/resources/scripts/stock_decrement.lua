-- KEYS: [1] stockKey, [2] infoKey, [3] userKey
-- ARGV: [1] quantity, [2] nowMilli

local stockKey = KEYS[1]
local infoKey = KEYS[2]
local userKey = KEYS[3]

local quantity = tonumber(ARGV[1])
local nowMilli = tonumber(ARGV[2])

-- 1. 판매 정책 정보 및 상태(Hash) 존재 여부 검증
local status = redis.call('HGET', infoKey, 'status')
local startAt = redis.call('HGET', infoKey, 'startAt')
local endAt = redis.call('HGET', infoKey, 'endAt')
local maxPurchaseQuantity = redis.call('HGET', infoKey, 'maxPurchaseQuantity')

-- 필수 정보가 없거나, 상태가 'ON_SALE'이 아닌 경우 (취소/중단/미개시 등)
if not status or status ~= 'ON_SALE' or not startAt or not endAt or not maxPurchaseQuantity then
    return -4 -- SALE_CLOSED
end

-- 2. 판매 기간 검증 (시작 시간 ~ 종료 시간)
startAt = tonumber(startAt)
endAt = tonumber(endAt)

if nowMilli < startAt or nowMilli > endAt then
    return -2 -- INVALID_SALE_PERIOD
end

-- 3. 1인당 구매 제한 수량 검증 (유저 누적 구매량 체크)
local userPurchased = redis.call('GET', userKey)
if not userPurchased then
    userPurchased = 0
else
    userPurchased = tonumber(userPurchased)
end

maxPurchaseQuantity = tonumber(maxPurchaseQuantity)
if (userPurchased + quantity) > maxPurchaseQuantity then
    return -3 -- EXCEEDED_PURCHASE_QUANTITY
end

-- 4. 재고 검증 및 차감
local stock = redis.call('GET', stockKey)
if not stock then
    return -4 -- SALE_CLOSED
end

stock = tonumber(stock)
if stock < quantity then
    return -1 -- NOT_ENOUGH_STOCK
end

-- 5. 차감 및 유저 구매 수량 갱신 (원자적 처리)
redis.call('DECRBY', stockKey, quantity)
redis.call('INCRBY', userKey, quantity)

-- 유저 Key 자동 삭제를 위한 TTL 부여 (24시간 = 86400초)
redis.call('EXPIRE', userKey, 86400)

-- 성공 시 1 반환 (Java 단에서 성공 판별용)
return 1