package com.stockflow.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a paginated slice of records along with pagination metadata.
 * Immutable and thread-safe.
 *
 * @param <T> the type of elements contained in the page
 */
public class PagedResult<T> {

    private final List<T> content;
    private final int pageNumber;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;

    public PagedResult(List<T> content, int pageNumber, int pageSize, long totalElements) {
        if (content == null) {
            throw new IllegalArgumentException("Content list cannot be null.");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 1.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be greater than or equal to 1.");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("Total elements cannot be negative.");
        }

        this.content = List.copyOf(content);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalElements == 0 ? 0 : (int) ((totalElements + pageSize - 1) / pageSize);
    }

    /**
     * Creates an empty paged result for a given page and size.
     */
    public static <T> PagedResult<T> empty(int pageNumber, int pageSize) {
        return new PagedResult<>(Collections.emptyList(), pageNumber, pageSize, 0L);
    }

    public List<T> getContent() {
        return content;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean isFirst() {
        return pageNumber == 1;
    }

    public boolean isLast() {
        return totalPages == 0 || pageNumber >= totalPages;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public int getNumberOfElements() {
        return content.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PagedResult<?> that = (PagedResult<?>) o;
        return pageNumber == that.pageNumber &&
                pageSize == that.pageSize &&
                totalElements == that.totalElements &&
                totalPages == that.totalPages &&
                Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, pageNumber, pageSize, totalElements, totalPages);
    }

    @Override
    public String toString() {
        return "PagedResult{" +
                "pageNumber=" + pageNumber +
                ", pageSize=" + pageSize +
                ", totalElements=" + totalElements +
                ", totalPages=" + totalPages +
                ", numberOfElements=" + content.size() +
                '}';
    }
}
