package com.example.data.model

enum class AppTheme(val title: String) {
  SYSTEM("Как на устройстве"),
  LIGHT("Светлая"),
  DARK("Тёмная");

  fun usesDarkColors(systemDark: Boolean): Boolean = this == DARK || (this == SYSTEM && systemDark)
}
