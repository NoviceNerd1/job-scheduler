-- pop_job.lua
-- Atomically pops the highest-score job from a priority queue and
-- registers a lease so no other worker can pick it up.
--
-- KEYS[1]: sorted set queue key  (e.g. queue:priority:1)
-- KEYS[2]: lease hash key        (job:leases)
-- ARGV[1]: worker ID
-- ARGV[2]: lease TTL in milliseconds

local result = redis.call('ZPOPMIN', KEYS[1], 1)
if result and #result > 0 then
    local jobId = result[1]
    redis.call('HSET', KEYS[2], jobId, ARGV[1])
    redis.call('PEXPIRE', KEYS[2], tonumber(ARGV[2]))
    return {jobId}
end
return {}
