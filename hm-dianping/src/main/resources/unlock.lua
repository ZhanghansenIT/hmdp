

-- 获取锁中的表示，判断是否与当前线程标识一致

if (redis.call('GET',KEYS[1]) == ARGV[1]  ) then

    -- 一致就 释放
    return redis.call('DEL',KEYS[1])
end
-- 不一致
return 0