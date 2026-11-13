cmake_minimum_required(VERSION 3.22.1)
project(rx3fakeplayer)

# Oboe viene como paquete prefab desde la dependencia Gradle com.google.oboe:oboe
find_package(oboe REQUIRED CONFIG)

add_library(rx3native SHARED
        native-lib.cpp)

target_link_libraries(rx3native
        oboe::oboe      # audio (AAudio/OpenSL vía Oboe)
        android         # ANativeWindow
        log)            # __android_log_print
