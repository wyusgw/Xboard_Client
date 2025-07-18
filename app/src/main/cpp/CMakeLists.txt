cmake_minimum_required(VERSION 3.22.1)

project("leaf_jni")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)

# 添加头文件路径
include_directories(${CMAKE_SOURCE_DIR}/../../../../leaf-android-libs)

# 添加源文件
add_library(
    leaf_jni
    SHARED
    leaf_jni.cpp
)

# 查找NDK提供的库
find_library(
    log-lib
    log
)

# 不在编译时链接leaf库，让它在运行时动态加载
target_link_libraries(
    leaf_jni
    ${log-lib}
)