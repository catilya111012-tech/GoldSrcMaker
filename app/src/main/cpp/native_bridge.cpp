#include <jni.h>

#include <android/log.h>
#include <fcntl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <string>

#define GOLDSRC_NATIVE_VERSION "0.1.0"
#define LOG_TAG "GoldSrcNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::string jstring_to_std(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return std::string();
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = (chars == nullptr) ? "" : chars;
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

const char* kStageBinaries[] = {"hlcsg", "hlbsp", "hlvis", "hlrad"};
const int kStageCount = 4;

bool file_exists(const std::string& path) {
    return access(path.c_str(), F_OK) == 0;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_goldsrc_maker_scripting_HybridScriptEngine_nativeRuntimeInfo(JNIEnv* env, jclass) {
    return env->NewStringUTF("native " GOLDSRC_NATIVE_VERSION);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_goldsrc_maker_compiler_CompilerNative_runStage(JNIEnv* env,
                                                        jclass,
                                                        jint stage,
                                                        jstring toolDir,
                                                        jstring workDir,
                                                        jstring mapName) {
    if (stage < 0 || stage >= kStageCount) {
        return -1;
    }

    const std::string tools = jstring_to_std(env, toolDir);
    const std::string work = jstring_to_std(env, workDir);
    const std::string map = jstring_to_std(env, mapName);

    const std::string binary = tools + "/lib" + kStageBinaries[stage] + ".so";
    if (!file_exists(binary)) {
        LOGE("stage %s: binary not found: %s", kStageBinaries[stage], binary.c_str());
        return 2;
    }

    const std::string logPath =
            work + "/compile_" + std::to_string(static_cast<int>(stage)) + ".log";

    const pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed for %s", kStageBinaries[stage]);
        return 3;
    }

    if (pid == 0) {
        if (chdir(work.c_str()) != 0) {
            _exit(127);
        }
        const int fd = open(logPath.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd >= 0) {
            dup2(fd, STDOUT_FILENO);
            dup2(fd, STDERR_FILENO);
            close(fd);
        }
        execl(binary.c_str(), kStageBinaries[stage], map.c_str(), static_cast<char*>(nullptr));
        _exit(127);
    }

    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        LOGE("waitpid failed for %s", kStageBinaries[stage]);
        return 3;
    }

    if (WIFEXITED(status)) {
        const int exitCode = WEXITSTATUS(status);
        LOGI("stage %s exited with %d", kStageBinaries[stage], exitCode);
        return exitCode;
    }

    LOGE("stage %s terminated abnormally", kStageBinaries[stage]);
    return 4;
}
