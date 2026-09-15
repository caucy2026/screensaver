# KEMI双屏屏保

当前版本 **1.0.14 / 10014**。只需安装一个 APK：`release/KEMI双屏屏保-v1.0.14-release.apk`。

提供银饰人像、春之舞两款屏保。点击卡片选择，蓝色边框和浅蓝底标识当前选择；启用与立即运行在同一屏完成。视频为 24fps 静音 HEVC，每段不超过 12 秒。

## 构建

依赖 JDK 17、Android SDK Platform 31、Build Tools 35.0.0、Python 3.9+、FFprobe 和 zip。在本目录设置 `JAVA_HOME`、`ANDROID_SDK_ROOT` 后执行 `SIGN_APKS=0 bash build.sh`，生成待签名 APK。正式签名由本地授权环境变量 `KEMI_WALLPAPER_KEYSTORE`、`KEMI_WALLPAPER_KEY_ALIAS`、`KEMI_WALLPAPER_STORE_PASSWORD`、`KEMI_WALLPAPER_KEY_PASSWORD` 注入，省略 `SIGN_APKS=0` 即可构建签名包。仓库不含密钥和口令。

构建缓存默认位于 `/Volumes/ORICO/kemi-build-cache/kwallpagers-douyin-screensaver-20260913`，外盘不可用时停止。预期平台证书 SHA-256：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

## 单包结构与升级

固定包名 `com.kemi.dualscreensaver`，同时包含界面、DreamService、双屏窗口和全部视频。选择界面使用 `:settings` 进程；播放服务及双屏窗口共用主进程中的播放器与纹理，不依赖旧服务 APK。

覆盖旧主程序后，升级接收器把指向 `com.kemi.dualscreensaver.service` 的三个系统屏保组件设置迁移到本包。首次打开界面也会补做迁移。其他应用的屏保选择不改动；主题选择和时间设置保留。历史 `AndroidManifest-service.xml` 已退出构建并删除。旧版本 release 文件和验证文档属于历史记录，不是当前分发包。

## S1 自动屏保兼容

启用时同步所选、默认及固件插电屏保组件。S1 固件正常屏保可能添加 DreamMirror 镜像层；在 D0 启动时做一次独立窗口接管。验收需覆盖普通系统 dream 入口及立即运行，检查两屏共享帧、循环、触摸退出和再次启动。视频帧率为 24fps，设备面板刷新率不因此变成 24Hz。
