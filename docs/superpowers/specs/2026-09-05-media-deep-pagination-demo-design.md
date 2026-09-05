# Media Deep Pagination Demo 设计说明

日期：2026-09-05  
状态：已确认，进入实现

## 1. 项目目标

构建一个可以公开展示和一键运行的 Java 后端项目，完整演示以下工程链路：

1. MySQL `LIMIT offset, size` 在深页上的成本；
2. 使用 Redis ZSet 保存分类下按发布时间排序的媒体 ID；
3. 先取有序 ID，再批量查询 MySQL 详情，并按 Redis 顺序重组；
4. 热点分类预热、缓存未命中互斥重建和临时 Key 原子切换；
5. 媒体发布、下线和修改分类后的缓存增量维护；
6. Redis 故障时限制深页、允许浅页受控回源；
7. 通过测试、指标和基准脚本验证正确性与性能趋势。

项目定位是通用的面试展示 Demo，不使用任何真实公司名称、内部协议、公司数据或不可验证的性能数字。

## 2. 非目标

- 不实现前端页面；
- 不拆分微服务；
- 不引入消息队列、搜索引擎或 Kubernetes；
- 不承诺跨多次分页请求的强一致快照；
- 不伪造生产 QPS、P95 或性能提升比例；
- 不把本项目描述为真实公司内部代码。

## 3. 技术选型

- Java 17；
- Spring Boot 3；
- Maven Wrapper；
- MySQL 8；
- Redis 7；
- MyBatis；
- Spring Data Redis；
- Flyway；
- Spring Boot Actuator 与 Micrometer；
- JUnit 5、Mockito、Testcontainers；
- Docker Compose；
- GitHub Actions。

分布式锁使用 Redis `SET NX PX`、唯一 token 和 Lua 安全释放。该实现用于展示互斥重建原理；设计会限制单次重建规模，并明确说明固定租约不等同于具备自动续期的完整生产级锁。

## 4. 总体架构

```text
HTTP Client
    ↓
MediaPageController
    ↓
MediaPageService
    ├── OFFSET 策略 → MySQL LIMIT offset,size
    └── ZSET 策略
          ├── Redis ZSet 取得 orderedIds
          ├── MySQL WHERE id IN (...) 批量查询详情
          └── Map<ID, Detail> 按 orderedIds 重组

MediaCommandService
    ↓ MySQL 事务
事务提交事件
    ↓
MediaIndexUpdater → Redis ZADD / ZREM

MediaIndexWarmupJob
    ↓
MediaIndexRebuilder → MySQL 分批读取 → 临时 ZSet → 原子切换
```

包结构按职责划分：

```text
api             Controller、请求与响应 DTO、异常映射
application     分页用例、媒体写用例、预热用例
domain          Media、分页策略、缓存状态等核心模型
infrastructure  MyBatis Mapper、Redis Repository、锁与时钟实现
config          属性、调度、线程池和可观测性配置
```

## 5. 数据模型

MySQL 表：

```sql
CREATE TABLE media (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    category_id  BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    publish_time DATETIME(3)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_media_category_status_publish_id
        (category_id, status, publish_time DESC, id DESC)
);
```

`status` 至少包含 `PUBLISHED` 和 `OFFLINE`。详情事实数据始终以 MySQL 为准。

## 6. HTTP API

### 6.1 分页查询

```http
GET /api/v1/categories/{categoryId}/media
    ?strategy=offset|zset
    &page=1
    &size=20
```

响应字段：

```json
{
  "categoryId": 1001,
  "page": 1,
  "size": 20,
  "total": 30000,
  "totalPages": 1500,
  "windowLimited": true,
  "strategy": "zset",
  "cacheStatus": "HIT",
  "degraded": false,
  "items": []
}
```

约束：`page >= 1`，`1 <= size <= 100`。超出缓存窗口的深页不自动回源 MySQL。

### 6.2 媒体写接口

```http
POST  /api/v1/media
PATCH /api/v1/media/{id}/status
PATCH /api/v1/media/{id}/category
PATCH /api/v1/media/{id}/publish-time
```

写接口用于演示发布时 `ZADD`、下线时 `ZREM`、修改分类时旧集合删除与新集合添加。

### 6.3 管理接口

```http
POST /api/v1/admin/categories/{categoryId}/cache/rebuild
```

该接口只用于本地 Demo，并在 README 中明确说明生产环境必须鉴权。

## 7. MySQL offset 分页

```sql
SELECT id, category_id, title, publish_time, status
FROM media
WHERE category_id = #{categoryId}
  AND status = 'PUBLISHED'
ORDER BY publish_time DESC, id DESC
LIMIT #{offset}, #{size};
```

`offset = (page - 1) * size`。联合索引改善过滤、排序和回表，但不能消除读取并丢弃 offset 前记录的成本。

项目额外提供游标 SQL 作为文档对照，但不作为主接口实现：

```sql
AND (
  publish_time < #{lastPublishTime}
  OR (publish_time = #{lastPublishTime} AND id < #{lastId})
)
ORDER BY publish_time DESC, id DESC
LIMIT #{size}
```

## 8. Redis ZSet 分页

### 8.1 Key 和成员

```text
正式索引：media:category:{categoryId}:publish
重建锁：  media:category:{categoryId}:publish:lock
临时索引：media:category:{categoryId}:publish:building:{uuid}
空值标记：media:category:{categoryId}:publish:empty
```

花括号中的分类 ID 是 Redis Cluster hash tag，使相关 Key 位于同一槽位。

```text
member = 固定宽度的媒体 ID 字符串
score  = publish_time 的 Unix 毫秒时间戳
```

毫秒时间戳处于 IEEE-754 double 可精确表达的整数范围内。固定宽度 member 使相同 score 时的字典序与数值 ID 顺序一致。

### 8.2 查询流程

```text
start = (page - 1) * size
end   = start + size - 1

ZREVRANGE key start end
    ↓ orderedIds
SELECT ... FROM media WHERE status='PUBLISHED' AND id IN (...)
    ↓ details
Map<ID, Detail>
    ↓ 按 orderedIds 依次获取并过滤 null
PageResponse
```

MySQL `IN` 不保证参数顺序，因此必须在 Java 中重组。ZSet 存在而 MySQL 缺少详情时，当前页允许少于 `size`；服务记录并异步 `ZREM` 失效 ID，不跨页强行补足。

`total` 使用 `ZCARD`，表示当前缓存窗口内可访问的条数，属于最终一致的缓存口径；它不是超大分类在 MySQL 中的精确总数。`totalPages = (total + size - 1) / size`。当索引达到缓存上限时返回 `windowLimited=true`，避免把窗口条数误解为数据库总数。

### 8.3 缓存窗口

Demo 仅缓存配置文件中明确指定的热点分类。单分类最多缓存可配置的前 N 条 ID，默认示例值为 30000。接口要求 `start < N` 且 `end < N`；例如 `size=10` 时第 3000 页对应 29990～29999，仍在窗口内，第 3001 页则被拒绝。

不假设“只存 ID 就没有内存成本”。README 提供 `MEMORY USAGE` 检查方法。百万级分类不全量进入一个 ZSet，更老数据使用游标查询或明确拒绝超出窗口的页码。

## 9. 缓存构建与预热

### 9.1 触发方式

- 启动后对配置的 Demo 热点分类执行一次预热；
- 定时任务在 Key 不存在或即将过期时刷新；
- 查询 miss 时执行互斥懒加载；
- 管理接口允许手动触发重建。

多实例环境使用全局任务锁避免每个实例重复运行预热；单个分类再使用分类级重建锁。

### 9.2 构建流程

```text
获得分类级锁
→ 二次检查正式 Key
→ MySQL 按 publish_time DESC、id DESC 使用联合游标分批读取前 N 条 id + publish_time
→ Redis Pipeline 分批写入临时 ZSet
→ 校验 ZCARD
→ 为临时 Key 设置带随机抖动的 TTL
→ RENAME 临时 Key 为正式 Key
→ 写入指标并释放锁
```

Pipeline 只减少网络往返，不提供事务原子性。临时 Key 隔离半成品，`RENAME` 作为单条 Redis 命令完成原子替换。源 Key 不存在、构建数量不符或写入失败时不触碰旧的正式 Key。

数据库分批查询使用 `(publish_time, id)` 作为联合游标，与线上排序和联合索引完全一致；不能仅按主键游标扫描后截断，否则得到的不一定是发布时间最新的前 N 条。`RENAME` 会保留临时 Key 上设置的 TTL。

数据库确实没有数据时，原子删除可能残留的正式索引并写入短 TTL 空值标记，防止旧索引继续提供错误总数，也防止不存在的分类持续穿透 MySQL。

## 10. 写路径和一致性

MySQL 是事实数据源，所有写操作先提交数据库事务。通过事务提交后的应用事件执行缓存更新：

```text
发布：MySQL commit → 正式 Key 已存在时 ZADD 新分类并裁剪到前 N 条
下线：MySQL commit → ZREM 原分类并触发该分类异步校准
改分类：MySQL commit → ZREM 旧分类 → 已有新分类索引时 ZADD → 两侧异步校准
改发布时间：MySQL commit → 正式 Key 已存在时 ZADD 更新 score 并裁剪
```

增量维护使用 Lua 将“检查正式 Key 存在、写入和窗口裁剪”组合为原子操作。正式 Key 不存在时不允许只写入单个成员，而是保留 miss 状态，让下一次预热或懒加载构建完整索引。发布到带空值标记的分类时先删除空值标记，再触发完整构建。删除成员可能让窗口尾部少一条，因此下线和改分类后安排异步全量校准；校准完成前允许短暂最终一致。

缓存操作失败时：

1. 记录带 categoryId、mediaId 和 traceId 的错误；
2. 将分类加入本实例的有界重试集合；
3. 后续定时任务全量重建；
4. TTL 最终淘汰旧索引；
5. 详情查询始终带 `status='PUBLISHED'`，避免下线内容仅因旧 ID 仍在 ZSet 而展示。

本实例的重试集合不是跨实例可靠队列；若 Redis 整体不可用，最终仍依赖 TTL 和定时全量预热修复。项目不引入消息队列。README 将 Outbox/可靠事件、延迟双删和版本化 Key 列为生产扩展，而不是声称本 Demo 已实现强一致。

## 11. 缓存未命中和击穿保护

缓存 miss 时仅一个请求获得分类级分布式锁并重建。其他请求进行有上限的短暂等待和重试：

- 浅页可在配置阈值内回源 MySQL；
- 深页不允许无条件回源；
- 超过等待预算后返回可识别的降级错误；
- 不返回 `null`，也不无限自旋。

锁使用唯一 token；释放锁的 Lua 脚本必须先比较 token 再删除，避免误删其他线程后来取得的锁。

## 12. Redis 故障降级

当 Redis 超时或不可用：

- offset 小于等于配置阈值时，使用 MySQL 浅页查询；
- 深页返回 HTTP 503 和业务码 `DEEP_PAGE_TEMPORARILY_UNAVAILABLE`；
- 记录降级次数、Redis 耗时和目标页码；
- 不在 Redis 故障时把所有任意深页流量打到 MySQL。

## 13. 可观测性

结构化日志字段：

```text
traceId, categoryId, strategy, page, size, offset,
redisCostMs, dbCostMs, reorderCostMs, totalCostMs,
cacheStatus, degraded, returnedCount
```

Micrometer 指标：

- offset 与 zset 两种策略的耗时分布；
- Redis 命中、miss、重建成功与失败次数；
- 深页降级次数；
- 失效 ID 清理数量；
- 缓存重建条数与耗时。

Actuator 暴露 health、metrics 和 prometheus 端点。普通日志不输出数据库密码或完整大对象。

## 14. 测试策略

### 14.1 单元测试

- 页码到 ZSet `start/end` 的换算；
- `totalPages` 向上取整；
- MySQL 无序详情按 `orderedIds` 重组；
- 缺失详情被过滤并生成清理任务；
- Redis 深页故障时拒绝回源；
- 浅页故障时允许回源；
- 锁 token 不匹配时不能删除锁；
- 相同发布时间下固定宽度 ID 的稳定顺序。

### 14.2 集成测试

使用 Testcontainers 启动真实 MySQL 和 Redis，覆盖：

- Flyway 建表和联合索引；
- offset 与 zset 返回内容及顺序一致；
- 临时 Key 构建后原子切换；
- 并发 miss 只有一个重建者；
- 发布、下线、改分类正确维护 ZSet；
- Redis 中残留无效 ID 时不返回下线内容。

### 14.3 基准验证

提供数据生成器和基准脚本，对相同分类、页大小和页码分别调用 offset 与 zset 接口，输出请求耗时。README 只描述复现实验的方法，不提交机器相关的结论性百分比。

## 15. 本地运行与交付

Docker Compose 包含：

- `app`：多阶段构建 Spring Boot 应用；
- `mysql`：MySQL 8，使用健康检查；
- `redis`：Redis 7，使用健康检查。

提供 `.env.example`，真实密码不提交 Git。README 包含：

1. 一键启动命令；
2. 数据生成命令；
3. 两种分页策略的 curl 示例；
4. 缓存重建和媒体写接口示例；
5. 架构与关键权衡；
6. 面试讲解顺序；
7. 已知限制和生产扩展。

## 16. CI 与 GitHub 发布

GitHub Actions 使用 JDK 17 执行：

```text
./mvnw --batch-mode verify
```

测试通过后构建应用镜像，但不推送镜像仓库。最终创建公开 GitHub 仓库 `media-deep-pagination-demo`，默认分支为 `main`，使用 MIT License。发布前检查仓库中不存在公司名称、内部数据、访问令牌或本机绝对路径。

## 17. 验收标准

- Docker Compose 文件、Flyway 迁移、数据生成器和 README 完整；
- offset 与 zset 接口对相同数据返回相同顺序；
- 第 3000 页的 ZSet 下标正确为 29990～29999（`size=10`）；
- MySQL `IN` 返回无序时仍按 Redis ID 顺序输出；
- 缓存重建不暴露临时半成品；
- 并发 miss 受到互斥保护；
- Redis 故障时深页不回源 MySQL；
- 单元测试和集成测试在 CI 中通过；
- GitHub 仓库公开可访问，且不包含敏感信息。
