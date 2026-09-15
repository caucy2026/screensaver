# KEMI双屏屏保

当前版本 **1.0.9-draft / 10009**，已签名并安装到 KEMI S1 测试设备 63。

提供银饰人像、春之舞两款屏保，点击预览选择，蓝色边框和浅蓝底标识当前选择；运行时循环所选视频。24fps 静音 HEVC，每段不超过 12 秒。启用与立即运行在同一屏完成，无设置入口及标题下说明。

最新图标为蓝色渐变、圆角玻璃双屏，文件 `res/drawable-nodpi/launcher_blue_glass.png`。历史 SVG 保留为设计记录，应用当前使用 PNG。

## 构建

在本 README 所在目录执行。依赖 JDK 17、Android SDK Platform 31、Build Tools 35.0.0、Python 3.9+、FFprobe 和 zip。

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=/path/to/android-sdk
export FFPROBE=/path/to/ffprobe
SIGN_APKS=0 ALLOW_INCOMPLETE=1 bash build.sh
```

默认构建缓存位于 `/Volumes/ORICO/kemi-build-cache/kwallpagers-douyin-screensaver-20260913`，不回退到系统盘；安装包输出到 `release/`。完整三视频版本仍缺指定抖音素材，所以当前两款体验版使用 `ALLOW_INCOMPLETE=1`，文件名及版本保留 draft 标记。

正式签名由本地获授权的环境变量 `KEMI_WALLPAPER_KEYSTORE`、`KEMI_WALLPAPER_KEY_ALIAS`、`KEMI_WALLPAPER_STORE_PASSWORD`、`KEMI_WALLPAPER_KEY_PASSWORD` 注入，构建时省略 `SIGN_APKS=0`。本仓库不包含密钥或口令。预期平台证书 SHA-256：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

## 结构与验证

UI 包 `com.kemi.dualscreensaver`，服务包 `com.kemi.dualscreensaver.service`。Dream 主窗口和另一屏共用同一播放器与纹理；D2 主窗口时，D0 使用应用自身普通全屏 Activity，不申请悬浮窗权限。

`docs/VALIDATION-1.0.9.json` 为最新安装记录；旧日期文档是历史记录。已经观察到两屏共享帧、所选春之舞连续循环，以及 D0 触摸退出释放播放器。D2 触摸退出及物理面板 24Hz 尚未完成验证。

`assets/` 包含可直接构建的最终媒体。`artwork/` 保留处理与设计记录，其中早期媒体处理脚本引用原外盘路径，属于历史记录；正常构建不需要重新生成媒体。
