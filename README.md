# Push to Talk

本项目为一个 Android 移动端应用，支持“按住说话”功能，适用于对讲、语音消息等场景。

基于网络实现，可以通过局域网点对点对讲或者是通过局域网广播实现范围对讲。

支持文字和语音消息的发送与接收。

## 背景

项目是本人移动互联网开发课程作业，但是做出来效果感觉还可以，因此开源在此供大家学习和使用。

## 功能特性
- 按住按钮即可录音并发送语音
- 支持本地录音存储与回放
- 简洁的用户界面

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

## 依赖
- Android SDK 32 及以上
- Gradle 构建工具

## 贡献
欢迎提交 issue 或 pull request 参与改进！

