package com.example.smartcityback.asset.shared;

import java.util.List;

public record PagedResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {}
