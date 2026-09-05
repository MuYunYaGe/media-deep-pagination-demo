# Media Deep Pagination Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public, runnable Spring Boot demo that compares MySQL offset pagination with Redis ZSet rank pagination and demonstrates ordered detail reassembly, safe index rebuilding, controlled degradation, and eventually consistent write maintenance.

**Architecture:** A single Spring Boot application exposes strategy-based media pagination and local administration APIs. MySQL remains the source of truth; Redis stores only a bounded ordered ID index per configured hot category, while focused application ports isolate query, index, lock, and command behavior for deterministic tests.

**Tech Stack:** Java 17, Spring Boot 3.3.13, Maven Wrapper 3.3.2, MyBatis 3.0.4, MySQL 8.4, Redis 7.4, Flyway, Spring Data Redis, Micrometer/Actuator, springdoc-openapi 2.6.0, JUnit 5, Mockito, Testcontainers 1.20.4, Docker Compose, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-05-media-deep-pagination-demo-design.md`

## Global Constraints

- Use Java 17 and Spring Boot 3; keep the application a single deployable service with no frontend.
- MySQL is the source of truth; Redis contains a bounded, eventually consistent ordered ID index only.
- The default per-category Redis window is exactly 30,000 IDs; a request is valid only when both rank bounds are inside that window.
- Sort published media by `publish_time DESC, id DESC`; encode Redis members as zero-padded, 20-character positive decimal IDs so equal scores follow descending numeric ID order under `ZREVRANGE`.
- Never represent the project as company-internal code, use company names or data, or publish unverifiable production performance claims.
- Do not add Kafka, Elasticsearch, Kubernetes, strong cross-request pagination snapshots, or automatic lock renewal.
- Every cache rebuild uses a temporary key, bounded pipeline writes, cardinality validation, a jittered TTL, and atomic `RENAME`.
- Redis failure may fall back to MySQL only when the requested offset is at or below the configured shallow-page threshold; deeper requests return HTTP 503.
- Keep secrets out of Git; commit only `.env.example` and development defaults.

---

## Locked File Structure

```text
.github/workflows/ci.yml                         build, test, and image smoke build
.mvn/wrapper/maven-wrapper.properties           Maven Wrapper distribution pin
docs/superpowers/specs/2026-09-05-media-deep-pagination-demo-design.md
                                                   approved design
docs/superpowers/plans/2026-09-05-media-deep-pagination-demo.md
                                                   this execution plan
scripts/benchmark.ps1                            repeatable Windows benchmark driver
scripts/benchmark.sh                             repeatable POSIX benchmark driver
src/main/java/com/example/mediapagination/
  MediaPaginationApplication.java                application entry point
  api/                                            REST DTOs, controllers, error mapping, trace filter
  application/model/                             use-case records and enums
  application/port/                              MySQL, Redis, lock, and index coordinator contracts
  application/query/                             offset/ZSet handlers, ordering, facade, stale cleanup
  application/cache/                             rebuild, coordination, warmup, dirty-category retry
  application/command/                           transactional media writes and post-commit updates
  config/                                         typed properties, async/scheduling configuration
  domain/                                         Media and MediaStatus
  infrastructure/mysql/                           MyBatis adapters and persistence rows
  infrastructure/redis/                           key codec, ZSet adapter, Lua-backed lease lock
src/main/resources/
  application.yml                                safe defaults and observability configuration
  application-local.yml                          local-only generator and admin settings
  db/migration/V1__create_media.sql              schema and composite index
  mapper/MediaQueryMapper.xml                     ordered MySQL queries
  mapper/MediaCommandMapper.xml                   insert/update/batch seed queries
src/test/java/com/example/mediapagination/        unit and container integration tests
src/test/resources/application-test.yml           deterministic test configuration
Dockerfile                                       multi-stage application image
compose.yml                                      app, MySQL, and Redis
README.md                                         runbook and interview narrative
LICENSE                                           MIT text
```

### Task 1: Bootstrap the Build and Pure Pagination Model

**Files:**
- Create: `pom.xml`
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Create: `.gitignore`, `.gitattributes`
- Create: `src/main/java/com/example/mediapagination/MediaPaginationApplication.java`
- Create: `src/main/java/com/example/mediapagination/domain/Media.java`
- Create: `src/main/java/com/example/mediapagination/domain/MediaStatus.java`
- Create: `src/main/java/com/example/mediapagination/application/model/PageStrategy.java`
- Create: `src/main/java/com/example/mediapagination/application/model/CacheStatus.java`
- Create: `src/main/java/com/example/mediapagination/application/model/MediaPageQuery.java`
- Create: `src/main/java/com/example/mediapagination/application/model/MediaPageResult.java`
- Create: `src/main/java/com/example/mediapagination/application/model/MediaIndexEntry.java`
- Create: `src/main/java/com/example/mediapagination/application/model/RankRange.java`
- Create: `src/main/java/com/example/mediapagination/application/model/PageOutsideWindowException.java`
- Create: `src/main/java/com/example/mediapagination/application/query/PageWindow.java`
- Create: `src/main/java/com/example/mediapagination/application/query/MediaOrderer.java`
- Create: `src/main/java/com/example/mediapagination/config/PaginationProperties.java`
- Create: `src/main/java/com/example/mediapagination/config/AsyncConfig.java`
- Create: `src/main/java/com/example/mediapagination/config/TimeConfig.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/mediapagination/application/query/PageWindowTest.java`
- Test: `src/test/java/com/example/mediapagination/application/query/MediaOrdererTest.java`

**Interfaces:**
- Produces: `RankRange PageWindow.ranks(int page, int size)` and `long PageWindow.offset(int page, int size)`.
- Produces: `List<Media> MediaOrderer.byIds(List<Long> orderedIds, List<Media> unorderedDetails)`.
- Produces: immutable records used by every later task; `MediaPageResult.asDegraded(CacheStatus)` returns a copy with `degraded=true`.

- [ ] **Step 1: Add the Maven build and wrapper metadata**

Use Spring Boot parent `3.3.13`, Java `17`, MyBatis starter `3.0.4`, springdoc `2.6.0`, Testcontainers `1.20.4`, and these dependency groups: web, validation, data-redis, actuator, prometheus registry, MyBatis, MySQL runtime, Flyway plus `flyway-mysql`, test starter, Spring Boot Testcontainers, MySQL Testcontainer, and Testcontainers JUnit. Configure compiler `-parameters`, Surefire for `*Test`, Failsafe for `*IT`, JaCoCo verification with a 60% line floor, and the Spring Boot plugin. Pin the wrapper distribution exactly:

```properties
wrapperVersion=3.3.2
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
```

Set `.gitattributes` so shell scripts use LF and `mvnw.cmd` uses CRLF. Ignore `target/`, `.idea/`, `.vscode/`, `.env`, and local log files.

- [ ] **Step 2: Write failing page-boundary and ordering tests**

```java
class PageWindowTest {
    private final PageWindow window = new PageWindow(30_000);

    @Test void pageThreeThousandMapsToExpectedRanks() {
        assertThat(window.ranks(3_000, 10)).isEqualTo(new RankRange(29_990, 29_999));
    }

    @Test void rejectsTheFirstPageOutsideTheWindow() {
        assertThatThrownBy(() -> window.ranks(3_001, 10))
            .isInstanceOf(PageOutsideWindowException.class);
    }

    @Test void usesLongArithmeticForOffset() {
        assertThat(window.offset(2_000_000_000, 100)).isEqualTo(199_999_999_900L);
    }
}
```

```java
class MediaOrdererTest {
    @Test void restoresRedisOrderAndDropsMissingDetails() {
        Media first = media(101L);
        Media third = media(103L);
        List<Media> actual = new MediaOrderer().byIds(
            List.of(103L, 102L, 101L), List.of(first, third));
        assertThat(actual).extracting(Media::id).containsExactly(103L, 101L);
    }
}
```

- [ ] **Step 3: Run the focused tests and confirm the intended failure**

Run: `./mvnw -q -Dtest=PageWindowTest,MediaOrdererTest test`

Expected: compilation fails because `PageWindow`, `MediaOrderer`, and their model types do not exist.

- [ ] **Step 4: Implement the immutable model and minimal pure logic**

Use these exact central types:

```java
public record Media(long id, long categoryId, String title,
                    Instant publishTime, MediaStatus status,
                    Instant createdAt, Instant updatedAt) {}

public record MediaPageQuery(long categoryId, PageStrategy strategy, int page, int size) {}

public record MediaPageResult(long categoryId, int page, int size,
        long total, long totalPages, boolean windowLimited,
        PageStrategy strategy, CacheStatus cacheStatus,
        boolean degraded, List<Media> items) {
    public MediaPageResult asDegraded(CacheStatus status) {
        return new MediaPageResult(categoryId, page, size, total, totalPages,
            windowLimited, strategy, status, true, items);
    }
}

public record RankRange(long start, long end) {}
public record MediaIndexEntry(long id, Instant publishTime) {}

public enum PageStrategy { OFFSET, ZSET }
public enum CacheStatus { NOT_USED, HIT, MISS_REBUILT, EMPTY_MARKER, FALLBACK_MYSQL }
public enum MediaStatus { PUBLISHED, OFFLINE }
```

`PageWindow` validates `page >= 1`, `1 <= size <= 100`, calculates with `Math.multiplyExact((long) page - 1, size)`, and rejects `end >= maxEntries`. `MediaOrderer` builds a `HashMap<Long, Media>` and streams `orderedIds`, filtering absent entries while preserving ID order.

- [ ] **Step 5: Add typed defaults and the application entry point**

Bind `demo.pagination` through `@ConfigurationProperties` with defaults: `window-size=30000`, `shallow-fallback-max-offset=1000`, `index-ttl=PT30M`, `empty-marker-ttl=PT2M`, `lock-ttl=PT2M`, `lock-wait=PT500MS`, `warmup-lock-ttl=PT10M`, `rebuild-batch-size=500`, and `hot-category-ids=[1001,1002]`. Enable scheduling, async execution, configuration-property scanning, and declare the application main class. Provide a UTC `Clock` bean and a bounded `cacheMaintenanceExecutor` (core 2, max 4, queue 200, caller-runs rejection); Task 9 adds MDC propagation to that executor without changing its sizing.

- [ ] **Step 6: Run tests and commit**

Run: `./mvnw -q -Dtest=PageWindowTest,MediaOrdererTest test`

Expected: both test classes pass.

```bash
git add pom.xml mvnw mvnw.cmd .mvn .gitignore .gitattributes src
git commit -m "build: bootstrap pagination demo"
```

### Task 2: Create the MySQL Source-of-Truth Query Adapter

**Files:**
- Create: `src/main/resources/db/migration/V1__create_media.sql`
- Create: `src/main/java/com/example/mediapagination/application/port/MediaQueryStore.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/mysql/MediaRow.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/mysql/MediaQueryMapper.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/mysql/MyBatisMediaQueryStore.java`
- Create: `src/main/resources/mapper/MediaQueryMapper.xml`
- Create: `src/test/java/com/example/mediapagination/support/ContainerIntegrationTest.java`
- Create: `src/test/java/com/example/mediapagination/infrastructure/mysql/MyBatisMediaQueryStoreIT.java`
- Create: `src/test/resources/application-test.yml`

**Interfaces:**
- Produces: `List<Media> findPublishedPage(long categoryId, long offset, int size)`.
- Produces: `long countPublished(long categoryId)`.
- Produces: `List<Media> findPublishedByIds(List<Long> ids)`; result order is deliberately unspecified.
- Produces: `Optional<Media> findById(long id)` for command-side before/after snapshots.
- Produces: `List<MediaIndexEntry> findPublishedIndexBatch(long categoryId, Instant beforeTime, Long beforeId, int limit)` sorted by `publish_time DESC, id DESC`.

- [ ] **Step 1: Write the schema and failing container test**

The migration must create `media` with an auto-increment BIGINT key, millisecond timestamps, `PUBLISHED`/`OFFLINE` status, and this exact supporting index:

```sql
CREATE INDEX idx_media_category_status_publish_id
    ON media (category_id, status, publish_time DESC, id DESC);
```

The test base starts `mysql:8.4` and `redis:7.4-alpine` using `@Testcontainers(disabledWithoutDocker = true)` and registers datasource plus Redis properties with `@DynamicPropertySource`.

```java
@Test
void indexBatchUsesPublishTimeAndIdAsAStableCursor() {
    insert(11, 1001, "same-newer-id", "2026-09-05T10:00:00Z", "PUBLISHED");
    insert(10, 1001, "same-older-id", "2026-09-05T10:00:00Z", "PUBLISHED");
    insert(12, 1001, "offline", "2026-09-05T11:00:00Z", "OFFLINE");

    List<MediaIndexEntry> first = store.findPublishedIndexBatch(1001, null, null, 1);
    List<MediaIndexEntry> second = store.findPublishedIndexBatch(
        1001, first.get(0).publishTime(), first.get(0).id(), 10);

    assertThat(first).extracting(MediaIndexEntry::id).containsExactly(11L);
    assertThat(second).extracting(MediaIndexEntry::id).containsExactly(10L);
}
```

- [ ] **Step 2: Run the integration test and confirm failure**

Run: `./mvnw -q -Dtest=MyBatisMediaQueryStoreIT test`

Expected with Docker: compilation fails because the query port and MyBatis adapter do not exist. Without Docker, compilation still fails before container detection.

- [ ] **Step 3: Implement the query port and adapter**

Use an XML `<resultMap>` for `MediaRow`, convert rows to domain records in `MyBatisMediaQueryStore`, and implement the stable cursor condition exactly:

```sql
WHERE category_id = #{categoryId}
  AND status = 'PUBLISHED'
<if test="beforeTime != null">
  AND (publish_time &lt; #{beforeTime}
       OR (publish_time = #{beforeTime} AND id &lt; #{beforeId}))
</if>
ORDER BY publish_time DESC, id DESC
LIMIT #{limit}
```

The offset query uses the same filter and order with `LIMIT #{offset}, #{size}`. The ID batch query includes `status='PUBLISHED'` and an XML `<foreach>` `IN` clause; do not add a MySQL `ORDER BY FIELD` expression because the application must demonstrate Java-side reassembly.

- [ ] **Step 4: Verify migration, cursor, offset, and unordered batch behavior**

Add assertions that `findPublishedPage(1001, 1, 1)` returns the second published row, `countPublished(1001)` returns `2`, and `findPublishedByIds(List.of(10L, 11L))` returns the correct set without asserting database order.

Run: `./mvnw -q -Dtest=MyBatisMediaQueryStoreIT test`

Expected with Docker: PASS. If Docker is unavailable: the test is explicitly skipped and unit tests remain runnable.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db src/main/resources/mapper src/main/java/com/example/mediapagination/application/port src/main/java/com/example/mediapagination/infrastructure/mysql src/test
git commit -m "feat: add ordered MySQL media queries"
```

### Task 3: Expose the Offset Pagination Strategy

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/query/MediaPageHandler.java`
- Create: `src/main/java/com/example/mediapagination/application/query/OffsetPageHandler.java`
- Create: `src/main/java/com/example/mediapagination/application/query/MediaPaginationFacade.java`
- Create: `src/main/java/com/example/mediapagination/api/MediaPageController.java`
- Create: `src/main/java/com/example/mediapagination/api/MediaPageResponse.java`
- Create: `src/main/java/com/example/mediapagination/api/ApiError.java`
- Create: `src/main/java/com/example/mediapagination/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/example/mediapagination/api/PageStrategyConverter.java`
- Create: `src/main/java/com/example/mediapagination/application/model/UnsupportedStrategyException.java`
- Test: `src/test/java/com/example/mediapagination/application/query/OffsetPageHandlerTest.java`
- Test: `src/test/java/com/example/mediapagination/api/MediaPageControllerTest.java`

**Interfaces:**
- Produces: `PageStrategy MediaPageHandler.strategy()` and `MediaPageResult query(MediaPageQuery query)`.
- Produces: `MediaPageResult MediaPaginationFacade.query(MediaPageQuery query)` by an immutable `EnumMap<PageStrategy, MediaPageHandler>` assembled in its constructor.
- Consumes: `MediaQueryStore`, `PageWindow`, and Task 1 model records.

- [ ] **Step 1: Write failing application and MVC tests**

```java
@Test
void offsetHandlerReturnsStablePageAndExactDatabaseTotal() {
    when(store.findPublishedPage(1001L, 10L, 10)).thenReturn(List.of(media(90L)));
    when(store.countPublished(1001L)).thenReturn(21L);

    MediaPageResult result = handler.query(new MediaPageQuery(1001L, OFFSET, 2, 10));

    assertThat(result.total()).isEqualTo(21);
    assertThat(result.totalPages()).isEqualTo(3);
    assertThat(result.items()).extracting(Media::id).containsExactly(90L);
    assertThat(result.cacheStatus()).isEqualTo(CacheStatus.NOT_USED);
}
```

```java
mockMvc.perform(get("/api/v1/categories/1001/media")
        .param("strategy", "offset").param("page", "2").param("size", "10"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.categoryId").value(1001))
    .andExpect(jsonPath("$.strategy").value("offset"));
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=OffsetPageHandlerTest,MediaPageControllerTest test`

Expected: compilation fails because the strategy handler, facade, controller, and DTOs do not exist.

- [ ] **Step 3: Implement the handler, facade, and endpoint**

`OffsetPageHandler` computes a long offset through `PageWindow`, calls the MySQL page and count methods, and calculates `(total + size - 1) / size`. It returns `windowLimited=false`, `strategy=OFFSET`, `cacheStatus=NOT_USED`, and `degraded=false`.

The controller signature is:

```java
@GetMapping("/api/v1/categories/{categoryId}/media")
MediaPageResponse page(@PathVariable long categoryId,
    @RequestParam(defaultValue = "zset") PageStrategy strategy,
    @RequestParam(defaultValue = "1") @Min(1) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size)
```

Configure `PageStrategy` JSON values through `@JsonValue` and query values through a Spring `Converter<String, PageStrategy>`, accepting lowercase `offset` and `zset`. Map validation and window violations to HTTP 400 with stable codes `INVALID_PAGE_REQUEST` and `PAGE_OUTSIDE_CACHE_WINDOW`.

- [ ] **Step 4: Verify focused tests and the application context**

Run: `./mvnw -q -Dtest=OffsetPageHandlerTest,MediaPageControllerTest test`

Expected: PASS, including `total=21`, `totalPages=3`, and lowercase strategy serialization.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/mediapagination/api src/main/java/com/example/mediapagination/application src/test/java/com/example/mediapagination/api src/test/java/com/example/mediapagination/application
git commit -m "feat: expose offset pagination API"
```

### Task 4: Implement Redis ZSet Index Primitives

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/model/IndexState.java`
- Create: `src/main/java/com/example/mediapagination/application/port/MediaIndexStore.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/redis/MediaIndexKeys.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/redis/MediaIdCodec.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/redis/RedisMediaIndexStore.java`
- Create: `src/main/resources/redis/add-if-present-and-trim.lua`
- Create: `src/main/resources/redis/mark-empty.lua`
- Test: `src/test/java/com/example/mediapagination/infrastructure/redis/MediaIdCodecTest.java`
- Test: `src/test/java/com/example/mediapagination/infrastructure/redis/RedisMediaIndexStoreIT.java`

**Interfaces:**
- Produces: `IndexState state(long categoryId)`, where `READY` means the formal ZSet exists, `EMPTY` means the short-lived empty marker exists, and `MISSING` means neither exists.
- Produces: `List<Long> reverseRange(long categoryId, RankRange ranks)` and `long cardinality(long categoryId)`.
- Produces: `boolean addIfPresentAndTrim(long categoryId, MediaIndexEntry entry, int maxEntries)`, `void remove(long categoryId, Collection<Long> ids)`, and `void clearEmptyMarker(long categoryId)`.
- Produces rebuild primitives: `String temporaryKey(long categoryId, UUID buildId)`, `void append(String key, List<MediaIndexEntry> entries)`, `long cardinality(String key)`, `void expire(String key, Duration ttl)`, `void replaceFormal(long categoryId, String temporaryKey)`, `void delete(String key)`, `void markEmpty(long categoryId, Duration ttl)`, and `Duration ttl(long categoryId)`.

- [ ] **Step 1: Write failing member-codec and Redis integration tests**

```java
class MediaIdCodecTest {
    @Test void equalScoresSortByDescendingNumericId() {
        MediaIdCodec codec = new MediaIdCodec();
        assertThat(List.of(9L, 10L, 2L).stream()
            .map(codec::encode).sorted(reverseOrder()).map(codec::decode))
            .containsExactly(10L, 9L, 2L);
    }

    @Test void encodingIsAlwaysTwentyCharacters() {
        assertThat(new MediaIdCodec().encode(Long.MAX_VALUE)).hasSize(20);
    }
}
```

```java
@Test
void reverseRangeAndAtomicTrimKeepOnlyNewestMembers() {
    seedFormal(1001, entry(1, 1000), entry(2, 2000));
    assertThat(store.addIfPresentAndTrim(1001, entry(3, 3000), 2)).isTrue();
    assertThat(store.reverseRange(1001, new RankRange(0, 9)))
        .containsExactly(3L, 2L);
}

@Test
void incrementalAddMustNotCreateAnIncompleteFormalIndex() {
    assertThat(store.addIfPresentAndTrim(404, entry(1, 1000), 30_000)).isFalse();
    assertThat(store.state(404)).isEqualTo(IndexState.MISSING);
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./mvnw -q -Dtest=MediaIdCodecTest,RedisMediaIndexStoreIT test`

Expected: compilation fails because the codec, keys, port, and Redis adapter do not exist.

- [ ] **Step 3: Implement key/member encoding and read primitives**

Generate keys with a Redis Cluster hash tag:

```java
String formal(long categoryId) { return "media:category:{" + categoryId + "}:publish"; }
String lock(long categoryId) { return formal(categoryId) + ":lock"; }
String building(long categoryId, UUID id) { return formal(categoryId) + ":building:" + id; }
String empty(long categoryId) { return formal(categoryId) + ":empty"; }
String warmupLock() { return "media:warmup:{job}:lock"; }
```

Encode IDs with `String.format(Locale.ROOT, "%020d", id)` after rejecting non-positive values. Convert scores using `entry.publishTime().toEpochMilli()`. Use `reverseRange` for `ZREVRANGE`, `zCard` for totals, and parse every returned member through `MediaIdCodec`.

- [ ] **Step 4: Implement atomic incremental maintenance and rebuild primitives**

The Lua script receives the formal key, score, encoded member, and max size. It must not create a key on cache miss:

```lua
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(tonumber(ARGV[3]) + 1))
return 1
```

Use pipelined `ZADD` only for a temporary key. `replaceFormal` verifies both keys share the category hash tag and calls Redis `RENAME`; `expire` is applied before rename because Redis preserves the source TTL. `markEmpty` uses one Lua script to delete the formal index and set the empty marker with its millisecond TTL. Empty markers store the literal `1`, never a Java `null`.

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -q -Dtest=MediaIdCodecTest,RedisMediaIndexStoreIT test`

Expected: codec tests pass; with Docker, integration tests prove reverse order, atomic trimming, non-creation on miss, TTL preservation, and empty-marker state.

```bash
git add src/main/java/com/example/mediapagination/application/model/IndexState.java src/main/java/com/example/mediapagination/application/port/MediaIndexStore.java src/main/java/com/example/mediapagination/infrastructure/redis src/main/resources/redis src/test/java/com/example/mediapagination/infrastructure/redis
git commit -m "feat: add Redis media index primitives"
```

### Task 5: Add ZSet Pagination, Reassembly, and Controlled Degradation

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/model/IndexLoadOutcome.java`
- Create: `src/main/java/com/example/mediapagination/application/model/DeepPageUnavailableException.java`
- Create: `src/main/java/com/example/mediapagination/application/port/MediaIndexCoordinator.java`
- Create: `src/main/java/com/example/mediapagination/application/query/ZsetPageHandler.java`
- Create: `src/main/java/com/example/mediapagination/application/query/StaleIndexCleaner.java`
- Modify: `src/main/java/com/example/mediapagination/api/GlobalExceptionHandler.java`
- Test: `src/test/java/com/example/mediapagination/application/query/ZsetPageHandlerTest.java`
- Test: `src/test/java/com/example/mediapagination/application/query/StaleIndexCleanerTest.java`

**Interfaces:**
- Produces for later implementation: `IndexLoadOutcome MediaIndexCoordinator.ensureAvailable(long categoryId)` and `void requestRebuild(long categoryId)`.
- Produces: `ZsetPageHandler implements MediaPageHandler` with strategy `ZSET`.
- Consumes: `MediaIndexStore`, `MediaQueryStore`, `MediaIndexCoordinator`, `MediaOrderer`, `PageWindow`, `PaginationProperties`, and `OffsetPageHandler`.

- [ ] **Step 1: Write failing tests for hit, stale IDs, and Redis failure**

```java
@Test
void hitFetchesDetailsOnceAndRestoresRedisOrder() {
    when(index.state(1001)).thenReturn(IndexState.READY);
    when(index.reverseRange(1001, new RankRange(10, 19))).thenReturn(List.of(9L, 7L, 8L));
    when(index.cardinality(1001)).thenReturn(30_000L);
    when(mysql.findPublishedByIds(List.of(9L, 7L, 8L)))
        .thenReturn(List.of(media(8L), media(9L), media(7L)));

    MediaPageResult result = handler.query(new MediaPageQuery(1001, ZSET, 2, 10));

    assertThat(result.items()).extracting(Media::id).containsExactly(9L, 7L, 8L);
    assertThat(result.total()).isEqualTo(30_000L);
    assertThat(result.windowLimited()).isTrue();
}

@Test
void staleIdMakesCurrentPageShortAndSchedulesCleanup() {
    readyIds(List.of(9L, 8L));
    when(mysql.findPublishedByIds(anyList())).thenReturn(List.of(media(9L)));
    assertThat(handler.query(query()).items()).hasSize(1);
    verify(cleaner).removeAsync(1001L, List.of(8L));
}

@Test
void redisFailureFallsBackOnlyForShallowOffset() {
    when(index.state(anyLong())).thenThrow(new RedisConnectionFailureException("down"));
    when(offset.query(shallowQuery())).thenReturn(offsetResult());
    assertThat(handler.query(shallowQuery()).degraded()).isTrue();
    assertThatThrownBy(() -> handler.query(deepQuery()))
        .isInstanceOf(DeepPageUnavailableException.class);
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=ZsetPageHandlerTest,StaleIndexCleanerTest test`

Expected: compilation fails because the coordinator contract, handler, and cleaner do not exist.

- [ ] **Step 3: Implement the state machine and ordered read path**

Use this deterministic flow:

```text
READY   -> ZREVRANGE -> empty page or one MySQL IN query -> Map reassembly -> optional stale cleanup
EMPTY   -> empty result with cacheStatus=EMPTY_MARKER
MISSING -> coordinator.ensureAvailable -> inspect READY/EMPTY once -> bounded fallback or 503
Redis exception at any Redis step -> shallow MySQL fallback or deep-page 503
```

`windowLimited` is `cardinality >= properties.windowSize()`. For ZSet responses, `total` is cardinality within the cached window and `totalPages` uses that value. Never fetch one MySQL row per ID, never reorder through SQL, and never scan later ranks to fill a page containing stale IDs.

- [ ] **Step 4: Implement asynchronous stale cleanup and API error mapping**

`StaleIndexCleaner.removeAsync(long categoryId, List<Long> staleIds)` uses the named cache-maintenance executor and calls `MediaIndexStore.remove`. It returns immediately for an empty list and logs category plus count, not all IDs. Map `DeepPageUnavailableException` to:

```json
{
  "code": "DEEP_PAGE_TEMPORARILY_UNAVAILABLE",
  "message": "Redis index is unavailable and this page is too deep for MySQL fallback",
  "traceId": "trace-123"
}
```

with HTTP 503.

- [ ] **Step 5: Run focused tests and commit**

Run: `./mvnw -q -Dtest=ZsetPageHandlerTest,StaleIndexCleanerTest,MediaPageControllerTest test`

Expected: PASS for hit ordering, missing-detail cleanup, empty marker, lazy-load outcome, shallow fallback, deep refusal, and facade routing to `zset`.

```bash
git add src/main/java/com/example/mediapagination src/test/java/com/example/mediapagination
git commit -m "feat: add ZSet pagination and degradation"
```

### Task 6: Build Indexes Safely with a Tokenized Redis Lease

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/model/RebuildMode.java`
- Create: `src/main/java/com/example/mediapagination/application/model/RebuildResult.java`
- Create: `src/main/java/com/example/mediapagination/application/port/LeaseLock.java`
- Create: `src/main/java/com/example/mediapagination/application/port/Lease.java`
- Create: `src/main/java/com/example/mediapagination/application/cache/MediaIndexRebuilder.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/redis/RedisLeaseLock.java`
- Create: `src/main/resources/redis/release-lock.lua`
- Test: `src/test/java/com/example/mediapagination/application/cache/MediaIndexRebuilderTest.java`
- Test: `src/test/java/com/example/mediapagination/infrastructure/redis/RedisLeaseLockIT.java`
- Test: `src/test/java/com/example/mediapagination/infrastructure/redis/RedisIndexRebuildIT.java`

**Interfaces:**
- Produces: `Optional<Lease> LeaseLock.tryAcquire(String key, Duration ttl)` and `boolean LeaseLock.release(Lease lease)`.
- Produces: `RebuildResult MediaIndexRebuilder.rebuild(long categoryId, RebuildMode mode)` where modes are `IF_ABSENT` and `FORCE`.
- Consumes: the stable composite-cursor query from Task 2 and temporary-index operations from Task 4.

- [ ] **Step 1: Write failing lock and rebuild tests**

```java
@Test
void aDifferentTokenCannotReleaseTheCurrentLease() {
    Lease first = lock.tryAcquire("media:category:{1001}:publish:lock", Duration.ofSeconds(30)).orElseThrow();
    assertThat(lock.release(new Lease(first.key(), "wrong-token"))).isFalse();
    assertThat(lock.tryAcquire(first.key(), Duration.ofSeconds(30))).isEmpty();
    assertThat(lock.release(first)).isTrue();
}
```

```java
@Test
void rebuildUsesCompositeCursorAndAtomicallyPublishesValidatedIndex() {
    when(mysql.findPublishedIndexBatch(1001, null, null, 2))
        .thenReturn(List.of(entry(5, 5000), entry(4, 4000)));
    when(mysql.findPublishedIndexBatch(1001, instant(4000), 4L, 1))
        .thenReturn(List.of(entry(3, 3000)));
    when(index.cardinality(anyString())).thenReturn(3L);

    RebuildResult result = rebuilder.rebuild(1001, FORCE);

    assertThat(result.entryCount()).isEqualTo(3);
    InOrder order = inOrder(index);
    order.verify(index).expire(anyString(), argThat(Duration::isPositive));
    order.verify(index).replaceFormal(eq(1001L), anyString());
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=MediaIndexRebuilderTest,RedisLeaseLockIT,RedisIndexRebuildIT test`

Expected: compilation fails because lease and rebuild types do not exist.

- [ ] **Step 3: Implement safe lock acquisition and release**

Acquire with `SET key token NX PX ttl`, where token is a random UUID. Release only through:

```lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
```

Always release in `finally`. A missing or expired lease is a failed release, not an exception. Document in the class Javadoc that the configurable fixed lease has no watchdog and rebuild work is capped at 30,000 entries to stay within the lease budget.

- [ ] **Step 4: Implement the bounded rebuild algorithm**

For `IF_ABSENT`, check `index.state(categoryId)` before locking and once again after locking. Read no more than `windowSize` entries in batches. Advance the cursor from the final `(publishTime, id)` in each batch. Write batches to one UUID temporary key, verify its cardinality equals the number read, add a TTL of `indexTtl + random(0..10%)`, then atomically rename it.

When MySQL returns no entries, atomically remove any old formal index and set the empty marker. On any read/write/count mismatch, delete only the temporary key and preserve the old formal key. Define `RebuildResult` as a record containing nested enum `Status { SKIPPED_PRESENT, SKIPPED_LOCKED, EMPTY, REBUILT, FAILED }`, entry count, and elapsed duration, with named factories such as `skippedLocked()` used by tests.

- [ ] **Step 5: Verify exact cap, TTL, old-index preservation, and commit**

Run: `./mvnw -q -Dtest=MediaIndexRebuilderTest,RedisLeaseLockIT,RedisIndexRebuildIT test`

Expected: PASS, including a 30,001-row fixture proving only 30,000 members are published, equal timestamps retain descending ID order, and an injected build failure leaves the previous formal key untouched.

```bash
git add src/main/java/com/example/mediapagination/application/cache src/main/java/com/example/mediapagination/application/model src/main/java/com/example/mediapagination/application/port src/main/java/com/example/mediapagination/infrastructure/redis src/main/resources/redis src/test
git commit -m "feat: rebuild indexes through atomic swap"
```

### Task 7: Coordinate Lazy Loads, Startup Warmup, and Manual Rebuilds

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/cache/DefaultMediaIndexCoordinator.java`
- Create: `src/main/java/com/example/mediapagination/application/cache/MediaIndexWarmupJob.java`
- Create: `src/main/java/com/example/mediapagination/application/port/Sleeper.java`
- Create: `src/main/java/com/example/mediapagination/api/AdminCacheController.java`
- Create: `src/main/java/com/example/mediapagination/api/RebuildResponse.java`
- Modify: `src/main/java/com/example/mediapagination/config/PaginationProperties.java`
- Test: `src/test/java/com/example/mediapagination/application/cache/DefaultMediaIndexCoordinatorTest.java`
- Test: `src/test/java/com/example/mediapagination/application/cache/MediaIndexWarmupJobTest.java`
- Test: `src/test/java/com/example/mediapagination/api/AdminCacheControllerTest.java`

**Interfaces:**
- Implements: `MediaIndexCoordinator.ensureAvailable(long)` and `requestRebuild(long)` from Task 5.
- Exposes: `POST /api/v1/admin/categories/{categoryId}/cache/rebuild`, returning rebuild status, entry count, and duration in milliseconds.
- Consumes: `MediaIndexRebuilder`, `MediaIndexStore`, `LeaseLock`, `MediaIndexKeys`, `PaginationProperties`, and a `Sleeper` functional interface injected for deterministic tests.

- [ ] **Step 1: Write failing bounded-wait and scheduling tests**

```java
@Test
void loserOfRebuildLockWaitsOnlyWithinBudgetThenSeesReadyIndex() {
    when(rebuilder.rebuild(1001, IF_ABSENT)).thenReturn(RebuildResult.skippedLocked());
    when(index.state(1001)).thenReturn(MISSING, MISSING, READY);

    assertThat(coordinator.ensureAvailable(1001)).isEqualTo(IndexLoadOutcome.AVAILABLE);
    verify(sleeper, times(2)).sleep(Duration.ofMillis(50));
}

@Test
void exhaustedWaitReturnsUnavailableWithoutSpinningForever() {
    when(rebuilder.rebuild(1001, IF_ABSENT)).thenReturn(RebuildResult.skippedLocked());
    when(index.state(1001)).thenReturn(MISSING);
    assertThat(coordinator.ensureAvailable(1001)).isEqualTo(IndexLoadOutcome.UNAVAILABLE);
    verify(sleeper, atMost(10)).sleep(any());
}
```

```java
@Test
void scheduledWarmupRebuildsOnlyMissingOrExpiringConfiguredCategories() {
    when(index.ttl(1001)).thenReturn(Duration.ofMinutes(2));
    when(index.ttl(1002)).thenReturn(Duration.ofMinutes(20));
    job.refreshConfiguredCategories();
    verify(rebuilder).rebuild(1001, FORCE);
    verify(rebuilder, never()).rebuild(1002, FORCE);
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=DefaultMediaIndexCoordinatorTest,MediaIndexWarmupJobTest,AdminCacheControllerTest test`

Expected: compilation fails because coordinator, warmup job, response, and controller do not exist.

- [ ] **Step 3: Implement bounded lazy-load coordination**

`ensureAvailable` first inspects `IndexState`, calls `rebuild(categoryId, IF_ABSENT)` only for `MISSING`, then maps `REBUILT` to `AVAILABLE`, `EMPTY` to `EMPTY`, and a lock loser to bounded polling. Poll every 50 ms until `lockWait` elapses; return `UNAVAILABLE` after the deadline or on interruption, restoring the thread interrupt flag.

`requestRebuild` runs `rebuild(categoryId, FORCE)` on `cacheMaintenanceExecutor`. Keep this method on a proxied bean so `@Async` is not bypassed by self-invocation.

- [ ] **Step 4: Implement configured warmup and local admin endpoint**

At `ApplicationReadyEvent`, refresh only configured category IDs. The scheduled refresh runs every `demo.pagination.warmup-interval` (default `PT5M`) and first acquires `media:warmup:{job}:lock` for `warmupLockTtl` (default `PT10M`) so multiple instances do not duplicate the scan. For each configured category, force rebuild only when state is `MISSING` or remaining TTL is at or below `refresh-before` (default `PT5M`). Category-level rebuild locks remain the final concurrency guard.

The manual endpoint calls `rebuild(categoryId, FORCE)` synchronously for an inspectable demo response. Annotate its OpenAPI description with “local demonstration endpoint; production deployments must add authorization.”

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -q -Dtest=DefaultMediaIndexCoordinatorTest,MediaIndexWarmupJobTest,AdminCacheControllerTest,ZsetPageHandlerTest test`

Expected: PASS for available/empty/locked outcomes, wait exhaustion, interrupt handling, TTL refresh selection, global job lock, and manual response mapping.

```bash
git add src/main/java/com/example/mediapagination/application/cache src/main/java/com/example/mediapagination/api src/main/java/com/example/mediapagination/config src/test
git commit -m "feat: coordinate cache warmup and lazy rebuilds"
```

### Task 8: Add Transactional Media Writes and Eventual Index Maintenance

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/model/CreateMediaCommand.java`
- Create: `src/main/java/com/example/mediapagination/application/model/ChangeMediaStatusCommand.java`
- Create: `src/main/java/com/example/mediapagination/application/model/ChangeMediaCategoryCommand.java`
- Create: `src/main/java/com/example/mediapagination/application/model/ChangePublishTimeCommand.java`
- Create: `src/main/java/com/example/mediapagination/application/model/MediaChangedEvent.java`
- Create: `src/main/java/com/example/mediapagination/application/model/MediaNotFoundException.java`
- Create: `src/main/java/com/example/mediapagination/application/port/MediaCommandStore.java`
- Create: `src/main/java/com/example/mediapagination/application/command/MediaCommandService.java`
- Create: `src/main/java/com/example/mediapagination/application/command/MediaIndexUpdater.java`
- Create: `src/main/java/com/example/mediapagination/application/cache/DirtyCategoryRegistry.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/mysql/MediaCommandMapper.java`
- Create: `src/main/java/com/example/mediapagination/infrastructure/mysql/MyBatisMediaCommandStore.java`
- Create: `src/main/resources/mapper/MediaCommandMapper.xml`
- Create: `src/main/java/com/example/mediapagination/api/MediaCommandController.java`
- Create: `src/main/java/com/example/mediapagination/api/CreateMediaRequest.java`
- Create: `src/main/java/com/example/mediapagination/api/ChangeStatusRequest.java`
- Create: `src/main/java/com/example/mediapagination/api/ChangeCategoryRequest.java`
- Create: `src/main/java/com/example/mediapagination/api/ChangePublishTimeRequest.java`
- Test: `src/test/java/com/example/mediapagination/application/command/MediaCommandServiceTest.java`
- Test: `src/test/java/com/example/mediapagination/application/command/MediaIndexUpdaterTest.java`
- Test: `src/test/java/com/example/mediapagination/application/cache/DirtyCategoryRegistryTest.java`
- Test: `src/test/java/com/example/mediapagination/MediaWriteLifecycleIT.java`

**Interfaces:**
- Produces: `Media MediaCommandStore.insert(CreateMediaCommand)`, `Media updateStatus(long, MediaStatus, Instant)`, `Media updateCategory(long, long, Instant)`, and `Media updatePublishTime(long, Instant, Instant)`; update methods throw `MediaNotFoundException` on zero affected rows.
- Produces: transactional `MediaCommandService.create`, `changeStatus`, `changeCategory`, and `changePublishTime`; each publishes one `MediaChangedEvent(before, after)` inside the transaction.
- Consumes after commit: `MediaIndexUpdater.onMediaChanged(MediaChangedEvent)`.

- [ ] **Step 1: Write failing transaction and cache-maintenance tests**

```java
@Test
void commandPublishesBeforeAndAfterOnlyAfterSuccessfulStoreChange() {
    Media before = published(10L, 1001L);
    Media after = offline(10L, 1001L);
    when(queryStore.findById(10L)).thenReturn(Optional.of(before));
    when(commandStore.updateStatus(10L, OFFLINE, clock.instant())).thenReturn(after);

    assertThat(service.changeStatus(new ChangeMediaStatusCommand(10L, OFFLINE))).isEqualTo(after);
    verify(events).publishEvent(new MediaChangedEvent(before, after));
}
```

```java
@Test
void publishingUpdatesOnlyAnAlreadyCompleteIndex() {
    MediaChangedEvent event = new MediaChangedEvent(offline(10, 1001), published(10, 1001));
    when(index.addIfPresentAndTrim(eq(1001L), any(), eq(30_000))).thenReturn(false);
    updater.onMediaChanged(event);
    verify(index).clearEmptyMarker(1001L);
    verify(coordinator).requestRebuild(1001L);
}

@Test
void categoryChangeRemovesOldMemberAndCalibratesBothSides() {
    updater.onMediaChanged(new MediaChangedEvent(published(10, 1001), published(10, 1002)));
    verify(index).remove(1001L, List.of(10L));
    verify(coordinator).requestRebuild(1001L);
    verify(coordinator).requestRebuild(1002L);
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=MediaCommandServiceTest,MediaIndexUpdaterTest,DirtyCategoryRegistryTest,MediaWriteLifecycleIT test`

Expected: compilation fails because command types, adapters, and updater do not exist.

- [ ] **Step 3: Implement database commands and transaction-bound events**

Use `@Transactional` on all service methods. Load the old media row before updates, use the application clock for `updated_at`, return the row after mutation, and publish exactly one before/after event. Handle the create case with `before=null`; require a positive category ID, a nonblank title of at most 200 characters, a non-null publication time, and a status enum.

Handle events with:

```java
@Async("cacheMaintenanceExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onMediaChanged(MediaChangedEvent event) {
    try {
        maintainIndex(event);
    } catch (RuntimeException failure) {
        event.affectedCategoryIds().forEach(dirtyCategories::mark);
        log.warn("media index maintenance failed; scheduled repair requested", failure);
    }
}
```

This guarantees cache work never runs for a rolled-back database transaction while keeping Redis outside the database transaction.

- [ ] **Step 4: Implement the transition matrix and bounded retry registry**

Apply this exact behavior:

```text
not published -> published: clear empty marker; conditional atomic add+trim; request rebuild when index absent
published -> not published: remove from old category; request old-category rebuild
published -> published, category changed: remove old; conditional add new; request both rebuilds
published -> published, time changed: conditional add updates score and trims; request rebuild when index absent
other changes: no Redis mutation
```

On Redis failure, add affected category IDs to `DirtyCategoryRegistry`, a `ConcurrentHashMap.newKeySet()` capped at 1,000 entries. The warmup job drains at most 100 dirty IDs per run and requests forced rebuilds; failed IDs are re-added. This registry is explicitly process-local and is not presented as a reliable distributed queue.

- [ ] **Step 5: Expose write endpoints and verify lifecycle integration**

Expose `POST /api/v1/media`, `PATCH /api/v1/media/{id}/status`, `PATCH /api/v1/media/{id}/category`, and `PATCH /api/v1/media/{id}/publish-time`. Return HTTP 201 for create, 200 for updates, 404 with `MEDIA_NOT_FOUND` for absent IDs, and validation errors as HTTP 400.

The container integration test creates a complete index, publishes an item, verifies it appears at the correct rank, takes it offline, verifies it is filtered immediately by MySQL and then removed from Redis, and moves a published item between categories.

Run: `./mvnw -q -Dtest=MediaCommandServiceTest,MediaIndexUpdaterTest,DirtyCategoryRegistryTest,MediaWriteLifecycleIT test`

Expected: PASS with Docker integration skipped only when Docker is unavailable.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/mediapagination src/main/resources/mapper src/test
git commit -m "feat: maintain media indexes after committed writes"
```

### Task 9: Propagate Trace IDs and Publish Operational Metrics

**Files:**
- Create: `src/main/java/com/example/mediapagination/api/TraceIdFilter.java`
- Modify: `src/main/java/com/example/mediapagination/config/AsyncConfig.java`
- Create: `src/main/java/com/example/mediapagination/config/MdcTaskDecorator.java`
- Create: `src/main/java/com/example/mediapagination/application/query/PaginationMetrics.java`
- Modify: `src/main/java/com/example/mediapagination/application/query/OffsetPageHandler.java`
- Modify: `src/main/java/com/example/mediapagination/application/query/ZsetPageHandler.java`
- Modify: `src/main/java/com/example/mediapagination/application/cache/MediaIndexRebuilder.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/mediapagination/api/TraceIdFilterTest.java`
- Test: `src/test/java/com/example/mediapagination/config/MdcTaskDecoratorTest.java`
- Test: `src/test/java/com/example/mediapagination/application/query/PaginationMetricsTest.java`

**Interfaces:**
- Produces: request header/response header `X-Trace-Id` and MDC key `traceId`.
- Produces: named `cacheMaintenanceExecutor` whose `TaskDecorator` copies the submitting thread's MDC map, installs it for the task, restores any prior worker context, and clears in `finally`.
- Produces metric names: `media.pagination.duration`, `media.pagination.redis.duration`, `media.pagination.db.duration`, `media.pagination.reorder.duration`, `media.index.rebuild.duration`, `media.index.rebuild.entries`, `media.index.stale.removed`, and `media.pagination.degraded`.

- [ ] **Step 1: Write failing filter and MDC propagation tests**

```java
@Test
void acceptsSafeIncomingTraceIdAndAlwaysCleansMdc() throws Exception {
    request.addHeader("X-Trace-Id", "trace-123");
    filter.doFilter(request, response, chainThatAssertsMdc("trace-123"));
    assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
    assertThat(MDC.get("traceId")).isNull();
}

@Test
void taskDecoratorCopiesThenCleansTheSubmittingContext() {
    MDC.put("traceId", "parent-1");
    Runnable decorated = decorator.decorate(() -> assertThat(MDC.get("traceId")).isEqualTo("parent-1"));
    MDC.clear();
    decorated.run();
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=TraceIdFilterTest,MdcTaskDecoratorTest,PaginationMetricsTest test`

Expected: compilation fails because the filter, decorator, executor configuration, and metric wrapper do not exist.

- [ ] **Step 3: Implement request and async context propagation**

Accept an incoming trace ID only when it matches `[A-Za-z0-9._-]{1,64}`; otherwise generate a lowercase UUID without dashes. Put it into MDC immediately before the chain, return it in the response header, and restore the prior MDC map in `finally` so nested tests and servlet thread reuse cannot leak context.

Configure `ThreadPoolTaskExecutor` with prefix `cache-maint-`, core size `2`, max size `4`, queue capacity `200`, `CallerRunsPolicy`, graceful shutdown, and `MdcTaskDecorator`. These bounded values are demo defaults exposed through configuration rather than claimed production sizing.

- [ ] **Step 4: Add low-cardinality metrics and structured summary logs**

Use tags only for `strategy`, `cacheStatus`, `outcome`, and `degraded`; never use category ID, media ID, page, or trace ID as metric tags. Those request-specific values belong in one completion log containing:

```text
traceId categoryId strategy page size offset redisCostMs dbCostMs
reorderCostMs totalCostMs cacheStatus degraded returnedCount
```

Time operations with injected `MeterRegistry` and `Clock`/`Timer.Sample`; do not log database credentials, request bodies, or entire ID lists. Expose only `health`, `info`, `metrics`, and `prometheus` Actuator endpoints.

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -q -Dtest=TraceIdFilterTest,MdcTaskDecoratorTest,PaginationMetricsTest,ZsetPageHandlerTest,MediaIndexRebuilderTest test`

Expected: PASS for incoming/generated IDs, cleanup after exceptions, worker-thread cleanup, metric names/tags, and degraded counters.

```bash
git add src/main/java/com/example/mediapagination src/main/resources/application.yml src/test
git commit -m "feat: add tracing and pagination metrics"
```

### Task 10: Add Deterministic Demo Data and End-to-End Parity Tests

**Files:**
- Create: `src/main/java/com/example/mediapagination/application/model/GenerateMediaCommand.java`
- Create: `src/main/java/com/example/mediapagination/application/model/SeedMedia.java`
- Create: `src/main/java/com/example/mediapagination/application/command/DemoDataGenerator.java`
- Modify: `src/main/java/com/example/mediapagination/application/port/MediaCommandStore.java`
- Modify: `src/main/java/com/example/mediapagination/infrastructure/mysql/MediaCommandMapper.java`
- Modify: `src/main/java/com/example/mediapagination/infrastructure/mysql/MyBatisMediaCommandStore.java`
- Modify: `src/main/resources/mapper/MediaCommandMapper.xml`
- Create: `src/main/java/com/example/mediapagination/api/DemoDataController.java`
- Create: `src/main/java/com/example/mediapagination/api/GenerateMediaRequest.java`
- Create: `src/main/resources/application-local.yml`
- Create: `scripts/benchmark.ps1`
- Create: `scripts/benchmark.sh`
- Test: `src/test/java/com/example/mediapagination/DemoDataGeneratorIT.java`
- Test: `src/test/java/com/example/mediapagination/PaginationParityIT.java`

**Interfaces:**
- Produces local-only endpoint: `POST /api/v1/admin/data/generate` with body `{"categoryId":1001,"count":30050,"batchSize":500,"seed":42}`.
- Produces: `long DemoDataGenerator.generate(GenerateMediaCommand command)` returning the inserted count.
- Produces: `void MediaCommandStore.insertSeedBatch(List<SeedMedia> rows)` with batches capped at 1,000.

- [ ] **Step 1: Write failing deterministic generation and parity tests**

```java
@Test
void sameSeedProducesStableTitlesAndTimestampCollisions() {
    generator.generate(new GenerateMediaCommand(1001, 25, 10, 42));
    List<Media> rows = queryStore.findPublishedPage(1001, 0, 25);
    assertThat(rows).hasSize(25);
    assertThat(rows).extracting(Media::title).first().isEqualTo("Demo media 000024");
    assertThat(rows.stream().map(Media::publishTime).distinct().count()).isLessThan(25);
}
```

```java
@Test
void offsetAndZsetReturnTheSameIdsForPageThreeThousand() {
    generator.generate(new GenerateMediaCommand(1001, 30_050, 500, 42));
    rebuilder.rebuild(1001, FORCE);

    MediaPageResult offset = facade.query(new MediaPageQuery(1001, OFFSET, 3000, 10));
    MediaPageResult zset = facade.query(new MediaPageQuery(1001, ZSET, 3000, 10));

    assertThat(zset.items()).extracting(Media::id)
        .containsExactlyElementsOf(offset.items().stream().map(Media::id).toList());
    assertThat(zset.total()).isEqualTo(30_000);
    assertThat(zset.windowLimited()).isTrue();
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `./mvnw -q -Dtest=DemoDataGeneratorIT,PaginationParityIT test`

Expected: compilation fails because generation types and batch insert support do not exist.

- [ ] **Step 3: Implement deterministic, bounded batch generation**

Generate rows in memory one batch at a time, never all 30,050 at once. Derive a fixed UTC base instant from the numeric seed, then add `(ordinal / 3)` milliseconds so every three rows share a timestamp and exercise the ID tie-breaker. Titles use `Demo media %06d`; all rows are `PUBLISHED`. Reject counts above 100,000, batch sizes outside 1..1,000, and category IDs below 1.

Keep the endpoint under `@Profile("local")`; it is absent in the default and production profiles. After insertion, synchronously force-rebuild that category and return inserted count plus rebuild result.

- [ ] **Step 4: Add benchmark drivers that report observations, not claims**

Both scripts accept base URL, category ID, page, size, warmup iterations, and measured iterations. Each calls the offset and ZSet URLs after warmups, captures client-observed milliseconds, and prints min/median/p95 without committing a result file. The PowerShell core is:

```powershell
$durations = foreach ($i in 1..$Iterations) {
  $watch = [Diagnostics.Stopwatch]::StartNew()
  Invoke-RestMethod -Uri $Uri | Out-Null
  $watch.Stop()
  $watch.Elapsed.TotalMilliseconds
}
$sorted = @($durations | Sort-Object)
$p95 = $sorted[[Math]::Min($sorted.Count - 1, [Math]::Ceiling($sorted.Count * 0.95) - 1)]
```

The POSIX script uses `curl --fail --silent --output /dev/null --write-out '%{time_total}'` and `awk` for summary statistics. Both state that results depend on the local machine, data distribution, and warmed caches.

- [ ] **Step 5: Verify generator, rank 29990..29999, and commit**

Run: `./mvnw -q -Dtest=DemoDataGeneratorIT,PaginationParityIT test`

Expected with Docker: PASS and prove page 3000 uses Redis ranks 29990 through 29999, MySQL batch order is irrelevant, timestamp ties are stable, and page 3001 is rejected. Without Docker: integration tests are skipped explicitly.

```bash
git add src/main/java/com/example/mediapagination src/main/resources src/test scripts
git commit -m "feat: add deterministic demo data and benchmarks"
```

### Task 11: Package, Document, and Verify the Public Repository

**Files:**
- Create: `Dockerfile`
- Create: `compose.yml`
- Create: `.dockerignore`
- Create: `.env.example`
- Create: `.github/workflows/ci.yml`
- Create: `README.md`
- Create: `LICENSE`
- Modify: `pom.xml`
- Test: `src/test/java/com/example/mediapagination/ApplicationContextIT.java`

**Interfaces:**
- Produces: `docker compose up --build` local environment on application port 8080, MySQL port 3306, and Redis port 6379.
- Produces: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`, health at `/actuator/health`, and metrics at `/actuator/prometheus`.
- Produces CI command: `./mvnw --batch-mode verify` followed by `docker build -t media-deep-pagination-demo:ci .`.

- [ ] **Step 1: Write the failing context smoke test**

```java
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT extends ContainerIntegrationTest {
    @Test void contextLoads() {}
}
```

Use test configuration that disables scheduling and startup warmup. The inherited container base supplies real MySQL and Redis dynamic properties; the test is explicitly skipped by Testcontainers when Docker is unavailable.

- [ ] **Step 2: Run full verification before packaging**

Run: `./mvnw --batch-mode verify`

Expected: all unit tests pass; Testcontainers tests pass when Docker is available and report explicit skips otherwise; JaCoCo meets the 60% line floor.

- [ ] **Step 3: Add reproducible container packaging**

The Dockerfile uses `maven:3.9.9-eclipse-temurin-17` to run `./mvnw --batch-mode -DskipTests package`, then copies the jar into `eclipse-temurin:17-jre-jammy`, runs as a non-root UID, exposes 8080, and starts with `java -jar /app/app.jar`.

Compose uses `mysql:8.4` and `redis:7.4-alpine`, health checks both dependencies, waits for healthy status before starting the app, activates the `local` Spring profile for the demo-data endpoint, and reads safe development credentials from environment variables with non-secret defaults mirrored in `.env.example`. Persist MySQL and Redis in named volumes. Do not mount source directories or the host Docker socket.

- [ ] **Step 4: Write the README as an interview-ready runbook**

Include these concrete sections:

```text
Problem: why LIMIT offset,size still scans/discards earlier index entries
Architecture: request -> ZREVRANGE IDs -> MySQL IN -> HashMap -> ordered response
Run: copy .env.example to .env; docker compose up --build
Generate: POST 30,050 deterministic rows; rebuild category 1001
Try: curl examples for offset and zset page 3000 size 10
Inspect: ZCARD, ZREVRANGE WITHSCORES, MEMORY USAGE, Actuator, Swagger
Benchmark: PowerShell and POSIX command examples, with no promised percentage
Consistency: DB-first writes, post-commit best-effort index updates, TTL/rebuild repair
Failure behavior: shallow fallback and deep 503
Interview explanation: key/member/score, 29990..29999, IN reordering, pipeline, rename
Limitations: bounded window, no cross-request snapshot, fixed lease, process-local retry set
Production extensions: Outbox, managed lock/watchdog, versioned ZSets, access-driven hot-key discovery
```

Add a Mermaid sequence diagram for the ZSet request path and a comparison table for offset, cursor, and bounded ZSet pagination. Clearly label the repository as a generic learning/demo implementation, not company code.

- [ ] **Step 5: Add CI and public-content guards**

The workflow runs on pushes and pull requests, sets up Temurin 17 with Maven caching, grants only `contents: read`, runs `./mvnw --batch-mode verify`, and smoke-builds the Docker image. Add a repository scan command that fails on Windows user-profile paths, private-key headers, or common token prefixes, while excluding `.git` and the scan expression itself.

Run locally:

```bash
git grep -n -I -E '[A-Za-z]:\\Users\\|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|gh[pousr]_[A-Za-z0-9_]+' -- ':!docs/superpowers/**'
```

Expected: no output.

- [ ] **Step 6: Run verification, inspect the repository, and commit**

Run:

```bash
./mvnw --batch-mode verify
docker compose config
docker build -t media-deep-pagination-demo:local .
git diff --check
git status --short
```

Expected: Maven verification succeeds; when Docker is installed, Compose validates and the image builds; no whitespace errors or unexpected generated files exist. If Docker is unavailable, record that environmental limitation and rely on GitHub Actions for the container build rather than claiming it ran locally.

```bash
git add .github .dockerignore .env.example Dockerfile compose.yml README.md LICENSE pom.xml src/test
git commit -m "docs: package the public deep pagination demo"
```

### Task 12: Final Evidence and Release Readiness

**Files:**
- Modify only if verification exposes a defect; keep each correction in the file that owns the behavior.

**Interfaces:**
- Verifies every acceptance criterion in the approved spec before GitHub publication.

- [ ] **Step 1: Run the authoritative verification set from a clean status**

```bash
./mvnw --batch-mode verify
git diff --check
git status --short
git log --oneline --decorate -12
```

Expected: exit code 0, no uncommitted files, and a readable sequence of focused commits.

- [ ] **Step 2: Exercise the API when Docker is available**

```bash
docker compose up --build -d
curl --fail http://localhost:8080/actuator/health
curl --fail -X POST http://localhost:8080/api/v1/admin/data/generate -H 'Content-Type: application/json' -d '{"categoryId":1001,"count":30050,"batchSize":500,"seed":42}'
curl --fail 'http://localhost:8080/api/v1/categories/1001/media?strategy=offset&page=3000&size=10'
curl --fail 'http://localhost:8080/api/v1/categories/1001/media?strategy=zset&page=3000&size=10'
```

Expected: health is `UP`; both page calls return the same ordered IDs; the ZSet response reports `total=30000`, `windowLimited=true`, `cacheStatus=HIT`, and ten items.

- [ ] **Step 3: Inspect artifacts and sensitive-content scan**

Confirm the README matches actual endpoint names and defaults, OpenAPI renders, `.env` is ignored, no `target/` content is tracked, no local absolute path is present, and the repository contains no company references, credentials, or benchmark result claims.

- [ ] **Step 4: Create and push the public GitHub repository**

Create public repository `media-deep-pagination-demo` with no auto-generated README, license, or `.gitignore` because those files already exist locally. Add the HTTPS remote as `origin`, push `main`, wait for GitHub Actions, and open the repository page plus the latest workflow run. Do not copy browser cookies or access tokens into terminal commands, logs, commits, or chat.

- [ ] **Step 5: Report release evidence**

Return the public repository URL, pushed commit hash, local unit/integration verification results, GitHub Actions result, Docker verification result or explicit local Docker limitation, and the three strongest interview talking points. Do not state that any check passed without its command or workflow showing a successful result.
