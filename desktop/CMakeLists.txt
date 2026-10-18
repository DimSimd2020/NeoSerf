cmake_minimum_required(VERSION 3.21)
project(NeoSerf VERSION 1.3.0 LANGUAGES CXX)
include(GNUInstallDirs)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
find_package(Qt6 6.8 REQUIRED COMPONENTS Widgets Network WebEngineWidgets Svg Sql Concurrent LinguistTools)

set(NEOSERF_SOURCES
    src/BrowserWindow.cpp src/BrowserWindow.h
    src/HomePage.cpp src/HomePage.h
    src/WeatherService.cpp src/WeatherService.h
    src/AddressResolver.cpp src/AddressResolver.h
    src/UiAssets.cpp src/UiAssets.h
    src/UiTheme.cpp
    src/Language.cpp src/Language.h
    src/BrowserImport.cpp src/BrowserImport.h
    src/ImportWidget.cpp src/ImportWidget.h
    src/WelcomeDialog.cpp src/WelcomeDialog.h
    src/Preferences.cpp src/Preferences.h
    src/SettingsDialog.cpp src/SettingsDialog.h
    resources.qrc)
add_library(neoserf_browser STATIC ${NEOSERF_SOURCES})
target_include_directories(neoserf_browser PUBLIC src)
target_link_libraries(neoserf_browser PUBLIC Qt6::Widgets Qt6::Network Qt6::WebEngineWidgets Qt6::Svg Qt6::Sql Qt6::Concurrent)
qt_add_translations(neoserf_browser
    TS_FILES translations/neoserf_en.ts
    RESOURCE_PREFIX "/i18n")
if(MSVC)
    target_compile_options(neoserf_browser PUBLIC /utf-8 /W4)
endif()
if(WIN32)
    target_link_libraries(neoserf_browser PRIVATE dwmapi)
endif()

qt_add_executable(NeoSerf WIN32 src/main.cpp)
target_link_libraries(NeoSerf PRIVATE neoserf_browser)
if(WIN32)
    target_sources(NeoSerf PRIVATE assets/neoserf.rc)
    target_include_directories(NeoSerf PRIVATE assets)
endif()
install(TARGETS NeoSerf RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
# Standard dialog buttons (Save, Cancel...) need Qt's own Russian catalog next to the app.
set(NEOSERF_QTBASE_RU "${QT6_INSTALL_PREFIX}/${QT6_INSTALL_TRANSLATIONS}/qtbase_ru.qm")
if(EXISTS "${NEOSERF_QTBASE_RU}")
    install(FILES "${NEOSERF_QTBASE_RU}" DESTINATION translations)
endif()
qt_generate_deploy_app_script(TARGET NeoSerf OUTPUT_SCRIPT deploy_script NO_UNSUPPORTED_PLATFORM_ERROR)
install(SCRIPT ${deploy_script})

include(CTest)
if(BUILD_TESTING)
    find_package(Qt6 REQUIRED COMPONENTS Test)
    qt_add_executable(neoserf_tests tests/BrowserTest.cpp)
    target_link_libraries(neoserf_tests PRIVATE neoserf_browser Qt6::Test)
    add_test(NAME neoserf_browser COMMAND neoserf_tests)
endif()
