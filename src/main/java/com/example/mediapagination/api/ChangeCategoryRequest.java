package com.example.mediapagination.api;

import jakarta.validation.constraints.Min;

public record ChangeCategoryRequest(@Min(1) long categoryId) {
}
