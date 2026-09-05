package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.domain.MediaStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface MediaCommandMapper {

    int insert(MediaRow row);

    int updateStatus(
            @Param("id") long id,
            @Param("status") MediaStatus status,
            @Param("updatedAt") Instant updatedAt);

    int updateCategory(
            @Param("id") long id,
            @Param("categoryId") long categoryId,
            @Param("updatedAt") Instant updatedAt);

    int updatePublishTime(
            @Param("id") long id,
            @Param("publishTime") Instant publishTime,
            @Param("updatedAt") Instant updatedAt);
}
