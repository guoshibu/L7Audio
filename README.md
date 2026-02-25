# L7音频工具

## 项目简介
L7音频工具是一款功能强大的Android音频处理应用，为银河L7提供车内车外音乐播放、麦克风放大、文字转语音等多种音频相关功能。
-基于Trae开发，全程代码基本都是AI操刀。
-对了，银河L7设置页面的车外输出设备填9、15、22都行，但是音乐模块想要车外功能只能填9，否则报错。
-应用默认值为15，记得自己改为9，懒得改代码了。

## 主要功能

### 🎵 音乐播放器
- 支持本地音乐文件播放
- 自动读取音乐文件的专辑封面和元数据
- 支持播放列表管理，防止重复添加
- 支持后台播放功能
- 支持多种循环模式（单曲循环、列表循环、随机播放）
- 支持播放进度保存和恢复

### 🎤 麦克风放大器
- 实时麦克风音频放大
- 噪声抑制功能
- 回声抑制功能
- 啸叫抑制功能
- 可调节放大级别

### 📢 文字转语音（TTS）
- 支持文本转语音功能
- 可调节语速和音调
- 支持多种语音引擎

### ⚙️ 设置
- 音频输出设备选择
- 应用主题设置
- 开机自启设置

## 技术栈

### 核心技术
- **开发语言**：Java
- **开发工具**：Android Studio、Trae
- **最低SDK**：Android 11 (API 30)
- **目标SDK**：Android 14 (API 34)

### 第三方库
- **Media3**：用于音乐播放
- **OkHttp**：用于网络请求
- **Gson**：用于JSON解析
- **WorkManager**：用于应用保活

## 安装说明

### 方法一：直接安装APK
1. 下载项目生成的APK文件
2. 在Android设备上允许安装来自"未知来源"的应用
3. 点击APK文件进行安装

### 方法二：从源码构建
1. 克隆本项目到本地
2. 使用Android Studio打开项目
3. 同步Gradle依赖
4. 构建并运行应用

## 使用方法

### 音乐播放器
1. 点击底部导航栏的"音乐"图标进入音乐播放器
2. 点击"添加音乐"按钮选择本地音乐文件
3. 点击"扫描音乐"按钮自动扫描设备中的音乐文件
4. 点击播放列表中的歌曲开始播放
5. 使用播放控制按钮控制播放、暂停、上一曲、下一曲
6. 拖动进度条调整播放位置

### 麦克风放大器
1. 点击底部导航栏的"麦克风"图标进入麦克风放大器
2. 调整"放大级别"滑块设置合适的放大程度
3. 启用或禁用"噪声抑制"、"回声抑制"、"啸叫抑制"功能
4. 点击"开始放大"按钮开始使用麦克风放大功能
5. 点击"停止放大"按钮停止使用

### 文字转语音
1. 点击底部导航栏的"TTS"图标进入文字转语音功能
2. 在文本输入框中输入要转换的文字
3. 点击"播放"按钮开始语音播放
4. 点击"停止"按钮停止语音播放

## 项目结构

```
L7Audio/
├── app/
│   ├── src/main/
│   │   ├── java/com/aug32/l7audio/
│   │   │   ├── audio/            # 音频相关核心类
│   │   │   │   ├── AudioFocusManager.java
│   │   │   │   ├── AudioOutputManager.java
│   │   │   │   ├── MicrophoneManager.java
│   │   │   │   ├── MusicPlayerManager.java
│   │   │   │   └── TTSManager.java
│   │   │   ├── service/          # 服务类
│   │   │   │   ├── AudioForegroundService.java
│   │   │   │   ├── KeepAliveManager.java
│   │   │   │   └── KeepAliveWorker.java
│   │   │   ├── AppConfig.java    # 应用配置
│   │   │   ├── AppLog.java       # 日志工具
│   │   │   ├── BootReceiver.java # 开机启动接收器
│   │   │   ├── MainActivity.java # 主活动
│   │   │   ├── MicAmplifierFragment.java # 麦克风放大器界面
│   │   │   ├── MusicPlayerFragment.java  # 音乐播放器界面
│   │   │   ├── MusicPlaylistAdapter.java # 音乐播放列表适配器
│   │   │   ├── SettingsFragment.java     # 设置界面
│   │   │   └── TTSFragment.java          # 文字转语音界面
│   │   ├── res/                  # 资源文件
│   │   │   ├── layout/           # 布局文件
│   │   │   ├── drawable/         # 图片资源
│   │   │   ├── values/           # 字符串、颜色等资源
│   │   │   └── menu/             # 菜单资源
│   │   └── AndroidManifest.xml   # 应用清单文件
│   ├── build.gradle.kts          # 应用级构建配置
│   └── proguard-rules.pro        # 代码混淆规则
├── build.gradle.kts              # 项目级构建配置
├── settings.gradle.kts           # 项目设置
├── gradlew                       # Gradle包装器脚本
├── gradlew.bat                   # Windows Gradle包装器脚本
└── README.md                     # 项目说明文档
```

## 贡献指南

### 如何贡献
1. Fork本项目
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开一个Pull Request

### 代码规范
- 遵循Java代码规范
- 确保代码注释清晰
- 保持代码风格一致

## 许可证信息

本项目采用MIT许可证：

```
MIT License

Copyright (c) 2026 L7音频工具

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 联系方式

如果您有任何问题或建议，欢迎联系我：

- 项目地址：[[GitHub链接](https://github.com/guoshibu/L7Audio)]()
- 项目地址：企鹅群-159045907

---

**享受您的车外音频体验！** 🎧
