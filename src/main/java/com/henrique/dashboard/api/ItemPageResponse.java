package com.henrique.dashboard.api;

import java.util.List;

public record ItemPageResponse(List<ItemResponse> items, long total, int page, int size) { }
