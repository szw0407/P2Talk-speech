# Push to Talk

本项目为一个 Android 移动端应用，支持“按住说话”功能，适用于对讲、语音消息等场景。

## 功能特性
- 按住按钮即可录音并发送语音
- 支持本地录音存储与回放
- 简洁的用户界面
- 可扩展集成第三方语音服务

## 项目结构
- `app/`：主应用模块
  - `src/main/java/`：Java 源代码
  - `src/main/res/`：资源文件（布局、图片等）
  - `src/main/AndroidManifest.xml`：应用清单
- `build.gradle`、`settings.gradle`：项目构建配置

## 构建与运行
1. 安装 [Android Studio](https://developer.android.com/studio)
2. 使用 Android Studio 打开本项目
3. 连接 Android 设备或启动模拟器
4. 点击“运行”按钮进行编译和安装

或使用命令行：
```sh
./gradlew assembleDebug
```
生成的 APK 位于 `app/build/outputs/apk/debug/`

## 依赖
- Android SDK 21 及以上
- Gradle 构建工具

## 贡献
欢迎提交 issue 或 pull request 参与改进！

## 许可证
MIT License
