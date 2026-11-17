# Click-package layout for the Ubuntu Touch reference build (Clickable `builder: pure-qml-cmake`).
# Clickable runs `DESTDIR=<install dir> make install` with CMAKE_INSTALL_PREFIX=/. so every DESTINATION is relative.
cmake_minimum_required(VERSION 3.16)
project(clavis-ut NONE)

# ---- OWNER-DECISION / OWNER-FILL (one place; program rule R11 wants a NAMES.md row before any listing) ----
set(APP_ID "clavis-reference.mbaliga")
set(APP_VERSION "0.1.0")
set(APP_MAINTAINER "Clavis maintainers <clavis@invalid.example>")
# ---------------------------------------------------------------------------------------------------------
set(CLICK_ARCH "all")
set(CLICK_FRAMEWORK "ubuntu-sdk-20.04")

if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/www/index.html)
  message(FATAL_ERROR "www/index.html is missing: run `python3 tools/stage_www.py` first (README.md)")
endif()

configure_file(manifest.json.in ${CMAKE_CURRENT_BINARY_DIR}/manifest.json @ONLY)
configure_file(clavis.desktop.in ${CMAKE_CURRENT_BINARY_DIR}/clavis.desktop @ONLY)

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/manifest.json ${CMAKE_CURRENT_BINARY_DIR}/clavis.desktop
              clavis.apparmor DESTINATION .)
install(FILES qml/Main.qml DESTINATION qml)
install(FILES assets/icon.png DESTINATION assets)
install(FILES www/index.html DESTINATION www)
