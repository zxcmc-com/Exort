package com.zxcmc.exort.gui;

record GuiPageWindow(int page, int totalPages, int pageSize) {
  GuiPageWindow {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive");
    }
    totalPages = Math.max(1, totalPages);
    page = Math.max(0, Math.min(page, totalPages - 1));
  }

  static GuiPageWindow forSlots(int requestedPage, int totalSlots, int pageSize) {
    if (totalSlots < 0) {
      throw new IllegalArgumentException("totalSlots must be non-negative");
    }
    int pages = Math.max(1, (int) Math.ceil(totalSlots / (double) pageSize));
    return new GuiPageWindow(requestedPage, pages, pageSize);
  }

  int displayPage() {
    return page + 1;
  }

  int startIndex() {
    return page * pageSize;
  }

  boolean hasNext() {
    return page + 1 < totalPages;
  }
}
