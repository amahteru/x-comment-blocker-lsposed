# X(Twitter) Comment Blocker (LSPosed)

用于自动屏蔽 X (Twitter) 官方 Android 客户端评论区垃圾信息、引流机器人与推广广告的 LSPosed 模块。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![LSPosed](https://img.shields.io/badge/LSPosed-Module-black)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)

## 功能

- **云端词库**：自动同步并定期更新公共垃圾屏蔽词库（与浏览器插件版共享云端规则源）。
- **自定义词库**：支持手动添加、编辑屏蔽词，并支持正则表达式（如 `/regex/i`）。
- **高级过滤**：
  - 按用户名或昵称包含屏蔽词过滤。
  - 自动拦截推广推文与广告（Promoted Tweets）。
  - 按特殊字符或花体字（数学粗体/斜体等防检测变体字符）过滤评论。
  - 按 Emoji 过滤垃圾评论。
  - 支持屏蔽包含 Grok 分享卡片的评论。
- **白名单机制**：支持将特定用户添加至白名单，白名单用户的评论将永远不会被屏蔽。
- **网络层无感拦截**：直接在 GraphQL 数据层剔除垃圾内容，无空白占位、无界面闪烁、零渲染开销。
- **匹配测试器**：内置规则实时测试工具，可即时验证评论或用户名是否命中拦截条件。
- **拦截统计**：实时记录并展示已拦截的评论与广告总数。

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

## 使用

- **全局控制**：通过主界面总开关随时启用或暂停模块拦截功能。
- **细项调节**：可单独开关推广广告拦截、用户名检测、花体字过滤、Emoji 过滤及 Grok 卡片过滤。
- **词库管理**：在“自定义屏蔽词库”中添加或删除词条（每行一条）。
- **云端同步**：点击“立即同步云端词库”可从 GitHub 或加速 CDN 拉取最新公共词库。
- **白名单管理**：在“白名单管理”中填入免受屏蔽的用户 handle（每行一个）。
- **效果测试**：在“测试匹配效果”中粘贴文本，可实时查看判定结论与拦截原因。

## 关联项目

- **浏览器插件版本**：[X(Twitter) Comment Blocker](https://github.com/amahteru/x-comment-blocker)（支持 Chrome / Edge / Firefox）
- **油猴脚本版本**：[X(Twitter) Comment Blocker Lite](https://github.com/amahteru/x-comment-blocker-lite)（轻量免扩展版）

## 隐私

所有过滤规则与数据均在设备本地处理。不收集任何账号信息、浏览记录或自定义词库内容。
网络请求仅用于向公开源获取云端词库更新。

## 协议

本项目基于 [MIT License](./LICENSE) 协议开源。
