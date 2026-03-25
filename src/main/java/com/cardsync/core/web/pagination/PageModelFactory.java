package com.cardsync.core.web.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PageModelFactory {

  public <E, M> PageModel<M> from(Page<E> page, Function<E, M> mapper) {
    List<M> content = new ArrayList<>(page.getNumberOfElements());
    for (E e : page.getContent()) {
      content.add(mapper.apply(e));
    }

    PageMeta meta = new PageMeta(
      page.getNumber(),
      page.getSize(),
      page.getTotalElements(),
      page.getTotalPages(),
      page.isFirst(),
      page.isLast()
    );

    return new PageModel<>(content, meta);
  }

  public <M> PageModel<M> fromMapped(Page<M> page) {
    PageMeta meta = new PageMeta(
      page.getNumber(),
      page.getSize(),
      page.getTotalElements(),
      page.getTotalPages(),
      page.isFirst(),
      page.isLast()
    );
    return new PageModel<>(page.getContent(), meta);
  }
}