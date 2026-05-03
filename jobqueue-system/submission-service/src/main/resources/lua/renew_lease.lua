-- KEYS[1]: lease hash key
-- ARGV[1]: job ID
-- ARGV[2]: worker ID
-- ARGV[3]: new TTL in milliseconds

local currentWorker = redis.call('HGET', KEYS[1], ARGV[1])
if currentWorker == ARGV[2] then
    redis.call('PEXPIRE', KEYS[1], ARGV[3])
    return 1
end
return 0
