package com.example.mediapagination.infrastructure.redis;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.port.MediaIndexStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisMediaIndexStore implements MediaIndexStore {

    private final StringRedisTemplate redis;
    private final MediaIndexKeys keys;
    private final MediaIdCodec codec;
    private final DefaultRedisScript<Long> addAndTrimScript;
    private final DefaultRedisScript<Long> markEmptyScript;

    public RedisMediaIndexStore(
            StringRedisTemplate redis, MediaIndexKeys keys, MediaIdCodec codec) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.addAndTrimScript = script("redis/add-if-present-and-trim.lua");
        this.markEmptyScript = script("redis/mark-empty.lua");
    }

    @Override
    public IndexState state(long categoryId) {
        if (Boolean.TRUE.equals(redis.hasKey(keys.formal(categoryId)))) {
            return IndexState.READY;
        }
        if (Boolean.TRUE.equals(redis.hasKey(keys.empty(categoryId)))) {
            return IndexState.EMPTY;
        }
        return IndexState.MISSING;
    }

    @Override
    public List<Long> reverseRange(long categoryId, RankRange ranks) {
        Objects.requireNonNull(ranks, "ranks");
        Set<String> members = redis.opsForZSet().reverseRange(
                keys.formal(categoryId), ranks.start(), ranks.end());
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(codec::decode).toList();
    }

    @Override
    public long cardinality(long categoryId) {
        return cardinality(keys.formal(categoryId));
    }

    @Override
    public boolean addIfPresentAndTrim(
            long categoryId, MediaIndexEntry entry, int maxEntries) {
        Objects.requireNonNull(entry, "entry");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        Long result = redis.execute(addAndTrimScript,
                List.of(keys.formal(categoryId)),
                Long.toString(entry.publishTime().toEpochMilli()),
                codec.encode(entry.id()),
                Integer.toString(maxEntries));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void remove(long categoryId, Collection<Long> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return;
        }
        Object[] members = ids.stream().map(codec::encode).toArray();
        redis.opsForZSet().remove(keys.formal(categoryId), members);
    }

    @Override
    public void clearEmptyMarker(long categoryId) {
        redis.delete(keys.empty(categoryId));
    }

    @Override
    public String temporaryKey(long categoryId, UUID buildId) {
        return keys.building(categoryId, buildId);
    }

    @Override
    public void append(String key, List<MediaIndexEntry> entries) {
        requireKey(key);
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return;
        }
        redis.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> stringOperations =
                        (RedisOperations<String, String>) (RedisOperations<?, ?>) operations;
                for (MediaIndexEntry entry : entries) {
                    stringOperations.opsForZSet().add(key, codec.encode(entry.id()),
                            entry.publishTime().toEpochMilli());
                }
                return null;
            }
        });
    }

    @Override
    public long cardinality(String key) {
        requireKey(key);
        Long count = redis.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }

    @Override
    public void expire(String key, Duration ttl) {
        requireKey(key);
        requirePositive(ttl, "ttl");
        redis.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void replaceFormal(long categoryId, String temporaryKey) {
        requireKey(temporaryKey);
        String formalKey = keys.formal(categoryId);
        if (!hashTag(formalKey).equals(hashTag(temporaryKey))) {
            throw new IllegalArgumentException("formal and temporary keys must share a Redis hash tag");
        }
        redis.rename(temporaryKey, formalKey);
        redis.delete(keys.empty(categoryId));
    }

    @Override
    public void delete(String key) {
        requireKey(key);
        redis.delete(key);
    }

    @Override
    public void markEmpty(long categoryId, Duration ttl) {
        requirePositive(ttl, "ttl");
        redis.execute(markEmptyScript,
                List.of(keys.formal(categoryId), keys.empty(categoryId)),
                Long.toString(ttl.toMillis()));
    }

    @Override
    public Duration ttl(long categoryId) {
        Long millis = redis.getExpire(keys.formal(categoryId), TimeUnit.MILLISECONDS);
        if (millis == null || millis < 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(millis);
    }

    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    private static String hashTag(String key) {
        int opening = key.indexOf('{');
        int closing = opening < 0 ? -1 : key.indexOf('}', opening + 1);
        if (opening < 0 || closing <= opening + 1) {
            throw new IllegalArgumentException("Redis key has no non-empty hash tag");
        }
        return key.substring(opening, closing + 1);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be blank");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
