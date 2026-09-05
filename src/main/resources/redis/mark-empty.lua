redis.call('DEL', KEYS[1])
redis.call('PSETEX', KEYS[2], ARGV[1], '1')
return 1
