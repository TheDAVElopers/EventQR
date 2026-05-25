package com.thedavelopers.eventqr.shared.response;

import java.util.List;

public record PaginatedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}