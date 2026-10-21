#include <jni.h>
#include <unistd.h>
#include "node.h"

extern "C" JNIEXPORT jint JNICALL
Java_top_izuna_ncmapi_NodeRunner_startNode(JNIEnv *env, jobject /* this */,
                                           jstring dataDir, jstring scriptPath) {
    const char *dir = env->GetStringUTFChars(dataDir, 0);
    const char *script = env->GetStringUTFChars(scriptPath, 0);

    if (chdir(dir) != 0) {
        // 目录切换失败时仍然尝试继续，node 会以当前目录解析脚本路径
    }

    char *argv[] = { (char *)"node", (char *)script, nullptr };
    int result = node::Start(2, argv);

    env->ReleaseStringUTFChars(dataDir, dir);
    env->ReleaseStringUTFChars(scriptPath, script);
    return result;
}

/** 通知 Node 事件循环停止，让 startNode 返回。 */
extern "C" JNIEXPORT void JNICALL
Java_top_izuna_ncmapi_NodeRunner_stopNode(JNIEnv *env, jobject /* this */) {
    node::Stop(0);
}