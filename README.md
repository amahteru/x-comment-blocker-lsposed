# X(Twitter) Comment Blocker (LSPosed)

用于自动屏蔽 X (Twitter) 官方 Android 客户端评论区垃圾信息与引流机器人的 LSPosed 模块。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![LSPosed](https://img.shields.io/badge/LSPosed-Module-black)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)

## 安装

### 1. Root 环境（LSPosed / KernelSU / APatch）

1. 下载并在手机上安装本模块 APK。
2. 打开 **LSPosed 管理器**，在模块列表中启用 **X Comment Blocker**。
3. 作用域勾选 **X (Twitter)**（`com.twitter.android`）。
4. 打开模块应用确认规则已同步，强行停止并重新打开 X 客户端即可生效。

### 2. 免 Root 环境（LSPatch 注入）

1. 在手机上安装 **LSPatch**。
2. 在 LSPatch 中选择 X (Twitter) 官方 APK 与本模块 APK 进行便携式修补打包。
3. 安装修补后的 APK 即可免 Root 使用。

## 关联项目

- **浏览器插件版本**：[X(Twitter) Comment Blocker](https://github.com/amahteru/x-comment-blocker)（支持 Chrome / Edge / Firefox）
- **油猴脚本版本**：[X(Twitter) Comment Blocker Lite](https://github.com/amahteru/x-comment-blocker-lite)（轻量免扩展版）

## 隐私

所有过滤规则与数据均在设备本地处理。不收集任何账号信息、浏览记录或自定义词库内容。
网络请求仅用于向公开源获取云端词库更新。

## 协议

本项目基于 [MIT License](./LICENSE) 协议开源。
