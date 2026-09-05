package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.application.model.MediaIndexEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MediaQueryMapper {

    List<MediaRow> findPublishedPage(
            @Param("categoryId") long categoryId,
            @Param("offset") long offset,
            @Param("size") int size);

    long countPublished(@Param("categoryId") long categoryId);

    List<MediaRow> findPublishedByIds(@Param("ids") List<Long> ids);

    Optional<MediaRow> findById(@Param("id") long id);

    List<MediaIndexEntry> findPublishedIndexBatch(
            @Param("categoryId") long categoryId,
            @Param("beforeTime") Instant beforeTime,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);
}
