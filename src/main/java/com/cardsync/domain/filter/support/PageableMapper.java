package com.cardsync.domain.filter.support;

import com.cardsync.domain.filter.query.SortDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public final class PageableMapper {
  private PageableMapper() {}

  public static Pageable toPageable(Integer page, Integer size, List<SortDto> sort) {
    int pageNumber = (page == null || page < 0) ? 0 : page;
    int pageSize = (size == null || size <= 0) ? 20 : size;

    Sort s = toSort(sort);
    return PageRequest.of(pageNumber, pageSize, s);
  }

  private static Sort toSort(List<SortDto> sort) {
    if (sort == null || sort.isEmpty()) return Sort.unsorted();

    var orders = sort.stream()
      .filter(x -> x != null && x.field() != null && !x.field().isBlank() && (x.order() != null))
      .map(x -> new Sort.Order(x.order() == 1 ? Sort.Direction.ASC : Sort.Direction.DESC, x.field()))
      .toList();

    return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
  }
}