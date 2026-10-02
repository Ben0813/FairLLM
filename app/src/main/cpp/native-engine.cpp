#include <jni.h>
#include <cstdio>
#include <exception>
#include <string>
#include <vector>

int llama_server(int argc, char ** argv);

extern "C" JNIEXPORT jint JNICALL
Java_be_itspace_fairllm_NativeEngine_run(JNIEnv * env, jobject, jobjectArray args, jstring log_path) {
    const char * log = env->GetStringUTFChars(log_path, nullptr);
    if (!log) return -1;
    FILE * file = std::freopen(log, "a", stderr);
    env->ReleaseStringUTFChars(log_path, log);
    if (!file) return -2;
    std::setvbuf(stderr, nullptr, _IOLBF, 0);
    std::vector<std::string> strings {"fairllm"};
    const jsize count = env->GetArrayLength(args);
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        if (!item) return -3;
        const char * value = env->GetStringUTFChars(item, nullptr);
        if (!value) { env->DeleteLocalRef(item); return -3; }
        strings.emplace_back(value);
        env->ReleaseStringUTFChars(item, value);
        env->DeleteLocalRef(item);
    }
    std::vector<char *> argv;
    for (auto & item : strings) argv.push_back(item.data());
    argv.push_back(nullptr);
    try {
        return llama_server(static_cast<int>(strings.size()), argv.data());
    } catch (const std::exception & e) {
        std::fprintf(stderr, "FairLLM: %s\n", e.what());
        return -4;
    }
}
