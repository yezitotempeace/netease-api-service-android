# 网易云 API 服务（Android）

**专门为 [Folia Android](https://github.com/yezitotempeace/folia-android) 制作的本地 API 服务 App。**

Folia Android 本身不带网易云接口，这个 App 在手机本地起一个 API 服务，供 Folia 调用。

## 它做什么

- 在 `127.0.0.1:3000` 提供网易云 API
- 内置 Node 运行时，**不需要 root、不需要电脑、不需要 Termux**
- 前台服务常驻，切后台、锁屏都不会断
- 通知栏显示运行状态
- App 内一键启动 / 停止，停止立即释放端口

## 安装与使用

1. 下载 APK 并安装
2. 打开 App，点「启动服务」
3. 状态变成「● 服务运行中」即可
4. 再打开 Folia Android，网易云功能就能用了

不用的时候点「停止服务」，进程会立刻结束，不会自己在后台重启。

## 说明

- 仅供个人学习与本地使用，不提供任何音乐内容
- 需要 Android 8.0 及以上（arm64）

## 许可证

本项目采用 **AGPL-3.0** 许可证，全文见 [LICENSE](LICENSE)。

## 源码

完整源码见本仓库 [`source/`](source) 目录，遵循 AGPL-3.0。
