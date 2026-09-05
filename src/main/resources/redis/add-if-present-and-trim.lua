if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
redis.call('ZREMRANGEBYRANK', KEYS[1], '0', tostring(-(tonumber(ARGV[3]) + 1)))
return 1
