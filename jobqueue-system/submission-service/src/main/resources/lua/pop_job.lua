-- KEYS[1]: queue key (e.g., queue:priority:1)
-- KEYS[2]: lease hash key
-- ARGV[1]: worker ID
-- ARGV[2]: lease TTL in milliseconds

local jobId = redis.call('ZPOPMIN', KEYS[1])
if jobId then
    redis.call('HSET', KEYS[2], jobId, ARGV[1])
    redis.call('PEXPIRE', KEYS[2], ARGV[2])
    return jobId
end
return nil
