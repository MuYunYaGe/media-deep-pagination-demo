package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;

public interface MediaPageHandler {

    PageStrategy strategy();

    MediaPageResult query(MediaPageQuery query);
}
