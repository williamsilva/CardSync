package com.cardsync.core.web.pagination;

import java.util.List;

public record PageModel<T>(
  List<T> content,
  PageMeta page
) {}