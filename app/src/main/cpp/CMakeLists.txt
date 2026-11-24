cmake_minimum_required(VERSION 3.22.1)

project("aaudioplayer")

add_library(
    aaudioplayer
    SHARED
    aaudio_player.cpp
)

target_link_libraries(
    aaudioplayer
    aaudio
    log
)
