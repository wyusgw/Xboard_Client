#include <jni.h>
#include <string>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <dlfcn.h>

#define LOG_TAG "LeafJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 动态加载的函数指针
typedef int32_t (*leaf_run_with_config_string_func)(uint16_t rt_id, const char *config);
typedef bool (*leaf_shutdown_func)(uint16_t rt_id);

static leaf_run_with_config_string_func leaf_run_with_config_string_ptr = nullptr;
static leaf_shutdown_func leaf_shutdown_ptr = nullptr;
static void* leaf_handle = nullptr;

struct LeafInstance {
    uint16_t rt_id;
    int tun_fd;
    pthread_t thread;
    bool running;
    std::string config;
    int result;
};

static LeafInstance* g_instance = nullptr;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

bool loadLeafLibrary() {
    if (leaf_handle != nullptr) {
        return true; // 已经加载
    }
    
    leaf_handle = dlopen("libleaf.so", RTLD_LAZY);
    if (!leaf_handle) {
        LOGE("无法加载libleaf.so: %s", dlerror());
        return false;
    }
    
    leaf_run_with_config_string_ptr = (leaf_run_with_config_string_func)dlsym(leaf_handle, "leaf_run_with_config_string");
    if (!leaf_run_with_config_string_ptr) {
        LOGE("无法找到leaf_run_with_config_string函数: %s", dlerror());
        dlclose(leaf_handle);
        leaf_handle = nullptr;
        return false;
    }
    
    leaf_shutdown_ptr = (leaf_shutdown_func)dlsym(leaf_handle, "leaf_shutdown");
    if (!leaf_shutdown_ptr) {
        LOGE("无法找到leaf_shutdown函数: %s", dlerror());
        dlclose(leaf_handle);
        leaf_handle = nullptr;
        return false;
    }
    
    LOGI("成功加载leaf库");
    return true;
}

void* leaf_thread_func(void* arg) {
    LeafInstance* instance = (LeafInstance*)arg;
    
    LOGI("启动Leaf实例，ID: %d", instance->rt_id);
    
    if (!loadLeafLibrary()) {
        LOGE("加载leaf库失败");
        pthread_mutex_lock(&g_mutex);
        instance->running = false;
        instance->result = -1;
        pthread_mutex_unlock(&g_mutex);
        return nullptr;
    }
    
    // 启动leaf - 这个函数会阻塞直到服务停止
    instance->result = leaf_run_with_config_string_ptr(instance->rt_id, instance->config.c_str());
    
    pthread_mutex_lock(&g_mutex);
    if (instance->result == 0) {
        LOGI("Leaf实例正常退出");
    } else {
        LOGE("Leaf实例退出，错误码: %d", instance->result);
    }
    instance->running = false;
    pthread_mutex_unlock(&g_mutex);
    
    return nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_byteflow_www_service_LeafVpnService_startLeaf(
    JNIEnv *env,
    jobject thiz,
    jstring config_json,
    jint tun_fd) {
    
    pthread_mutex_lock(&g_mutex);
    
    if (g_instance != nullptr) {
        LOGE("Leaf实例已经存在");
        pthread_mutex_unlock(&g_mutex);
        return 0;
    }
    
    const char* config_str = env->GetStringUTFChars(config_json, nullptr);
    if (config_str == nullptr) {
        LOGE("获取配置字符串失败");
        pthread_mutex_unlock(&g_mutex);
        return 0;
    }
    
    LOGI("Leaf配置: %s", config_str);
    
    g_instance = new LeafInstance();
    g_instance->rt_id = 1; // 使用固定ID
    g_instance->tun_fd = tun_fd;
    g_instance->running = true;
    g_instance->config = std::string(config_str);
    g_instance->result = -1;
    
    env->ReleaseStringUTFChars(config_json, config_str);
    
    // 创建线程运行leaf
    int result = pthread_create(&g_instance->thread, nullptr, leaf_thread_func, g_instance);
    if (result != 0) {
        LOGE("创建Leaf线程失败: %d", result);
        delete g_instance;
        g_instance = nullptr;
        pthread_mutex_unlock(&g_mutex);
        return 0;
    }
    
    // 等待一小段时间让线程启动
    pthread_mutex_unlock(&g_mutex);
    usleep(100000); // 100ms
    
    LOGI("Leaf实例启动成功");
    return (jlong)g_instance;
}

extern "C" JNIEXPORT void JNICALL
Java_com_byteflow_www_service_LeafVpnService_stopLeaf(
    JNIEnv *env,
    jobject thiz,
    jlong instance_id) {
    
    pthread_mutex_lock(&g_mutex);
    
    if (g_instance == nullptr || (jlong)g_instance != instance_id) {
        LOGE("无效的Leaf实例ID");
        pthread_mutex_unlock(&g_mutex);
        return;
    }
    
    LOGI("停止Leaf实例，ID: %d", g_instance->rt_id);
    
    // 关闭leaf
    if (leaf_shutdown_ptr != nullptr) {
        bool shutdown_result = leaf_shutdown_ptr(g_instance->rt_id);
        LOGI("Leaf关闭结果: %s", shutdown_result ? "成功" : "失败");
    } else {
        LOGE("leaf_shutdown函数未加载");
    }
    
    g_instance->running = false;
    pthread_mutex_unlock(&g_mutex);
    
    // 等待线程结束
    pthread_join(g_instance->thread, nullptr);
    
    pthread_mutex_lock(&g_mutex);
    delete g_instance;
    g_instance = nullptr;
    pthread_mutex_unlock(&g_mutex);
    
    LOGI("Leaf实例已停止");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_byteflow_www_service_LeafVpnService_isLeafRunning(
    JNIEnv *env,
    jobject thiz,
    jlong instance_id) {
    
    pthread_mutex_lock(&g_mutex);
    
    bool running = false;
    if (g_instance != nullptr && (jlong)g_instance == instance_id) {
        running = g_instance->running;
    }
    
    pthread_mutex_unlock(&g_mutex);
    
    return running;
}