package com.example.mediapagination.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "demo.pagination")
public class PaginationProperties {

    @Min(1)
    private int windowSize = 30_000;

    @Min(0)
    private long shallowFallbackMaxOffset = 1_000;

    @NotNull
    private Duration indexTtl = Duration.ofMinutes(30);

    @NotNull
    private Duration emptyMarkerTtl = Duration.ofMinutes(2);

    @NotNull
    private Duration lockTtl = Duration.ofMinutes(2);

    @NotNull
    private Duration lockWait = Duration.ofMillis(500);

    @NotNull
    private Duration warmupLockTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration warmupInterval = Duration.ofMinutes(5);

    @NotNull
    private Duration refreshBefore = Duration.ofMinutes(5);

    @Min(1)
    @Max(1_000)
    private int rebuildBatchSize = 500;

    private List<Long> hotCategoryIds = new ArrayList<>(List.of(1001L, 1002L));

    private boolean warmupEnabled = true;

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public long getShallowFallbackMaxOffset() {
        return shallowFallbackMaxOffset;
    }

    public void setShallowFallbackMaxOffset(long shallowFallbackMaxOffset) {
        this.shallowFallbackMaxOffset = shallowFallbackMaxOffset;
    }

    public Duration getIndexTtl() {
        return indexTtl;
    }

    public void setIndexTtl(Duration indexTtl) {
        this.indexTtl = indexTtl;
    }

    public Duration getEmptyMarkerTtl() {
        return emptyMarkerTtl;
    }

    public void setEmptyMarkerTtl(Duration emptyMarkerTtl) {
        this.emptyMarkerTtl = emptyMarkerTtl;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getWarmupLockTtl() {
        return warmupLockTtl;
    }

    public void setWarmupLockTtl(Duration warmupLockTtl) {
        this.warmupLockTtl = warmupLockTtl;
    }

    public Duration getWarmupInterval() {
        return warmupInterval;
    }

    public void setWarmupInterval(Duration warmupInterval) {
        this.warmupInterval = warmupInterval;
    }

    public Duration getRefreshBefore() {
        return refreshBefore;
    }

    public void setRefreshBefore(Duration refreshBefore) {
        this.refreshBefore = refreshBefore;
    }

    public int getRebuildBatchSize() {
        return rebuildBatchSize;
    }

    public void setRebuildBatchSize(int rebuildBatchSize) {
        this.rebuildBatchSize = rebuildBatchSize;
    }

    public List<Long> getHotCategoryIds() {
        return List.copyOf(hotCategoryIds);
    }

    public void setHotCategoryIds(List<Long> hotCategoryIds) {
        this.hotCategoryIds = new ArrayList<>(hotCategoryIds);
    }

    public boolean isWarmupEnabled() {
        return warmupEnabled;
    }

    public void setWarmupEnabled(boolean warmupEnabled) {
        this.warmupEnabled = warmupEnabled;
    }
}
