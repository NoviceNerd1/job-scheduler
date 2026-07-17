-- renew_lease.lua
-- Renews the lease TTL only if this worker still owns the lease.
-- Prevents a slow worker from extending a stolen lease.
--
-- KEYS[1]: lease hash key  (job:leases)
-- ARGV[1]: job ID
-- ARGV[2]: worker ID
-- ARGV[3]: new TTL in milliseconds

local currentWorker = redis.call('HGET', KEYS[1], ARGV[1])
if currentWorker == ARGV[2] then
    redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]))
    return 1
end
return 0
