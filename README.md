# NeLuaJ+ Builder

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2+-purple.svg)]()

**APK Build Tool for [NeLuaJ+](https://github.com/znzsofficial/NeLuaJ) Projects**

[English](#english) | [简体中文](#简体中文)

---

## English

### About

NeLuaJ+ Builder is a companion tool for the [NeLuaJ+](https://github.com/znzsofficial/NeLuaJ) Lua IDE. It takes your Lua project and packages it into a standalone Android APK — modifying the package name, version, permissions, icons, and more — then signs it ready for distribution.

### Features

- 📦 **One-tap APK Build** — Decode base APK → inject Lua project → repackage → sign
- ✏️ **Config Editor** — Edit app name, package name, version, SDK targets, debug mode
- 🔐 **Signature Management** — Built-in keystore generation with customizable signing config
- 📋 **Permission Manager** — Browse and select Android permissions from a searchable list
- 🧹 **Class Removal** — Decompile to Smali and selectively remove Java classes
- 🎨 **Material Design 3** — Dynamic Colors, MD3 Expressive grouped card layout
- 🌐 **Bilingual UI** — English and Chinese

### How It Works

```
NeLuaJ+ (IDE)          NeLuaJ+ Builder
┌──────────┐           ┌──────────────────┐
│ Write Lua │  ──────>  │ Select Project   │
│ Scripts   │           │ Edit Config      │
│ Test Run  │           │ Build APK        │
└──────────┘           │ Sign & Export    │
                        └──────────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │ Standalone   │
                        │ .APK File    │
                        └──────────────┘
```

### Requirements

- Android 7.0+ (API 24)
- [NeLuaJ+](https://github.com/znzsofficial/NeLuaJ) installed as the base app
- Storage permission for reading projects from `/sdcard/LuaJ/Projects/`

### Building from Source

```bash
git clone https://github.com/znzsofficial/NeLuaJ-Builder.git
cd NeLuaJ-Builder
./gradlew assembleRelease
```

Output APK: `app/build/outputs/apk/release/`

### Project Structure

```
app/src/main/java/com/nekolaska/
├── adapter/        # RecyclerView adapters
├── apk/            # APK editing, signing, manifest manipulation
├── base/           # Base Fragment with utility extensions
├── data/           # Data models (ProjectItem, InitConfig)
├── dialog/         # Config, Permission, Signing, Progress dialogs
├── fragments/      # UI screens (ProjectList, Detail, Build)
├── ktx/            # Kotlin extension functions
├── utils/          # CrashHandler, Toaster, PermissionHelper
└── view/           # Custom views
```

### License

```
Copyright 2024 znzsofficial

Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for the full text.

### Archive Notice

> **This project is no longer actively maintained.** Feel free to fork and continue development. Issues and PRs are welcome but may not receive timely responses.

---

## 简体中文

### 关于

NeLuaJ+ Builder 是 [NeLuaJ+](https://github.com/znzsofficial/NeLuaJ) Lua IDE 的配套打包工具。它可以将你的 Lua 项目打包成独立的 Android APK —— 修改包名、版本号、权限、图标等配置，然后签名生成可分发的安装包。

### 功能特性

- 📦 **一键打包** — 反编译基础 APK → 注入 Lua 项目 → 重新打包 → 签名
- ✏️ **配置编辑** — 编辑应用名称、包名、版本号、SDK 版本、调试模式
- 🔐 **签名管理** — 内置密钥库生成，支持自定义签名配置
- 📋 **权限管理** — 从可搜索的列表中浏览和选择 Android 权限
- 🧹 **类移除** — 反编译为 Smali 并选择性移除 Java 类
- 🎨 **Material Design 3** — 动态取色、MD3 Expressive 分组卡片布局
- 🌐 **双语界面** — 支持中文和英文

### 工作流程

```
NeLuaJ+ (编辑器)        NeLuaJ+ Builder (打包器)
┌──────────┐           ┌──────────────────┐
│ 编写 Lua  │  ──────>  │ 选择项目         │
│ 脚本      │           │ 编辑配置         │
│ 测试运行  │           │ 打包 APK         │
└──────────┘           │ 签名并导出       │
                        └──────────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │  独立 APK    │
                        │  安装包      │
                        └──────────────┘
```

### 使用要求

- Android 7.0+（API 24）
- 已安装 [NeLuaJ+](https://github.com/znzsofficial/NeLuaJ) 作为基础应用
- 存储权限（用于读取 `/sdcard/LuaJ/Projects/` 中的项目）

### 从源码构建

```bash
git clone https://github.com/znzsofficial/NeLuaJ-Builder.git
cd NeLuaJ-Builder
./gradlew assembleRelease
```

输出 APK 位于：`app/build/outputs/apk/release/`

### 许可证

```
Copyright 2024 znzsofficial

基于 Apache License 2.0 开源
```

完整协议见 [LICENSE](LICENSE)。

### 归档声明

> **本项目已不再积极维护。** 欢迎 Fork 并继续开发。Issue 和 PR 仍然欢迎提交，但可能无法及时回复。
