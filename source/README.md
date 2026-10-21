# 网易云 API 服务（Android）· 源码

专门为 [Folia Android](https://github.com/yezitotempeace/folia-android) 制作的本地 API 服务 App 的源码。

## 结构

- `project/`：Node 侧入口（`main.js`）与依赖声明（`package.json`）
- `android/`：Android 工程
  - `NodeService.java`：独立 `:node` 进程 + 前台服务，管理 Node 生命周期
  - `NodeRunner.java` / `node-start.cpp`：JNI 启动 `node::Start`
  - `MainActivity.java`：启动/停止界面与状态显示
- `build.sh`：构建脚本

## 构建

1. `cd project && npm install`
2. 把 `project/`（含 `node_modules`）打成 `ncm.zip`，放到 `android/app/src/main/assets/`
3. 准备 `libnode.so`（nodejs-mobile v18.20.4 arm64）与自编译的 `libncmnode.so`，放入 `android/app/src/main/jniLibs/arm64-v8a/`
4. `cd android && ./gradlew assembleDebug`

JNI 编译需要 Android NDK（aarch64 构建机可用 clang + bionic sysroot 替代，见 `android/app/src/main/cpp/`）。

## 许可证

AGPL-3.0，见 [LICENSE](LICENSE)。
