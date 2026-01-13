package com.zxcmc.exort.gui;

public interface SearchableSession extends GuiSession {
  String getSearchQuery();

  void setSearchQuery(String query);

  void clearSearch();
}
