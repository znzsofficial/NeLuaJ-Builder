# NeLuaJ+ Builder 维护说明

本文面向后续维护者，记录 NeLuaJ+ Builder 与主应用之间的稳定协议、配置安全边界、构建流水线、签名与安装流程，以及修改后的验证方法。

## 1. 仓库与版本基线

- Builder 仓库：`NeLuaJ+Builder`
- 主应用仓库：`NeLuaJ+`
- Builder applicationId / namespace：`com.nekolaska.Builder`
- Builder 入口 Activity：`com.nekolaska.MainActivity`
- 当前 Android 配置：`minSdk 26`、`targetSdk 37`、`compileSdk 37`
- 当前工具链：Gradle daemon JDK 21、应用 Java/Kotlin toolchain 17、Gradle 9.5、AGP 9.3.1、Kotlin 2.4.10
- APKEditor 源码位于 `app/src/main/java/com/reandroid/apkeditor/`，当前基于 1.4.x 代码。

版本号和工具链可能升级，因此修改前应以 `app/build.gradle.kts`、`gradle/libs.versions.toml` 和 `gradle/wrapper/gradle-wrapper.properties` 为准。

## 2. NeLuaJ+ 到 Builder 的打开协议

这是两个应用之间的稳定契约，修改时必须同步更新两端。

| 项目 | 值 |
|---|---|
| Builder package | `com.nekolaska.Builder` |
| Builder Activity | `com.nekolaska.MainActivity` |
| Intent action | `com.nekolaska.Builder.action.OPEN_PROJECT` |
| Project path extra | `com.nekolaska.Builder.extra.PROJECT_PATH` |

主应用调用端：

- `NeLuaJ+/app/src/main/assets/activities/main/Actions.lua`
- `Actions.openBuild()` 校验当前工程和 `init.lua`，保存当前编辑文件，然后使用显式 `ComponentName` 启动 Builder。
- `NeLuaJ+/app/src/main/AndroidManifest.xml` 通过 `<queries>` 声明 Builder 包可见性。

Builder 接收端：

- `app/src/main/AndroidManifest.xml` 声明公开的 `OPEN_PROJECT` intent-filter，并使用 `singleTop`。
- `MainActivity.onNewIntent()` 处理 Builder 已运行时的新工程。
- 待打开路径会跨 Activity 重建保存，并在存储权限返回后继续处理。
- 连续到达多个 Intent 时只打开最新路径，旧异步任务不得清除或打开新请求。

Builder 不会收到 Intent 后自动开始构建，只会进入对应的 `ProjectDetailFragment`。

## 3. 工程目录与存储权限

Builder 只接受以下根目录中的工程：

```text
/sdcard/LuaJ/Projects/<project>/
```

接收外部路径时必须：

1. 转换为 canonical path。
2. 确认目标为目录。
3. 确认 canonical path 位于 canonical `LuaJ/Projects` 根目录下。
4. 确认工程根目录存在常规文件 `init.lua`。

构建产物保存到：

```text
/sdcard/LuaJ/Builds/
```

Android 11 及以上使用 `MANAGE_EXTERNAL_STORAGE`；低版本使用读写外部存储权限。相关入口在 `MainActivity` 和 `NeedPermissionFragment`。

## 4. `init.lua` 安全模型

Builder 不执行项目的 `init.lua`。不要恢复通过 LuaJ `loadfile` 或其他方式执行项目配置的实现，因为项目文件是不可信输入，执行配置可能运行任意 Lua/Java 代码。

核心文件：

- `app/src/main/java/com/nekolaska/ktx/io/LuaConfig.kt`
- `app/src/main/java/com/nekolaska/ktx/io/LuaConfigParser.kt`
- `app/src/main/java/com/nekolaska/data/InitConfig.kt`
- `app/src/main/java/com/nekolaska/data/InitConfigWriter.kt`

解析器只接受受限字面量：

- 引号字符串和 Lua 长字符串
- 数字
- `true`、`false`、`nil`
- 有深度限制的字面量表
- 普通全局赋值
- `return { ... }` 配置表

安全约束：

- 配置最大 1 MiB、最多 100,000 个 token、表嵌套最大 16 层。
- 受管字段使用计算表达式时应拒绝读取或保存，不能静默回退到默认值。
- `user_permission` 必须是纯字符串数组，不接受布尔值、命名字段或计算表达式。
- `return {}` 中未知的计算型自定义字段可以保留，但不得执行。
- 不支持的结构应明确失败，不要猜测其语义。

受管字段及兼容别名包括：

```text
app_name / appname
package_name
ver_name / version_name
ver_code / version_code
debug_mode / debugmode
target_sdk
min_sdk
NeLuaJ_Theme
user_permission
```

Legacy `theme` 仅在值为 `Theme_NeLuaJ_*` 时作为 NeLuaJ 主题兼容字段同步。平台主题或资源表达式应原样保留。

### 精确写回

`InitConfigWriter` 只替换解析得到的值范围，不应整文件重写。必须保留：

- 注释和换行风格
- 未知字段与项目代码
- 旧字段别名
- 逗号和分号
- 普通全局赋值或 `return {}` 的原结构

写回后会再次解析并核对所有受管字段。任何验证失败都应保留原文件；`InitConfig.dumpToFile()` 通过临时文件和备份替换避免数据损坏。

## 5. 构建流水线

主要入口：`app/src/main/java/com/nekolaska/fragments/AppProcessFragment.kt`。

当前流水线：

1. 固化 UI 构建选项。
2. 校验并复制已安装的 NeLuaJ+ 基础 APK；split APK 当前不支持。
3. APKEditor 解包或恢复解包缓存。
4. 修改 package、应用名、manifest、资源、图标，以及可选的 `welcome.lua` 启动页。
5. 复制工程文件，按选项预编译 Lua。
6. 可选地让用户删除反编译后的 Smali 类。
7. 重新构建 APK。
8. 使用 `MySigner` 签名。
9. 将 APK 和可选 `.idsig` 成组、事务式导出到 `LuaJ/Builds`。
10. 清理构建 workspace，并显示成功对话框。

### 启动页 `welcome.lua`

工程根目录可以有可选的 `welcome.lua`。没有它时，保留基础 APK 里的 `welcome.xml`。有它时，`WelcomeXml` 在替换 `icon.png` 之后生成新的窗口背景，并覆盖反编译资源中所有名为 `welcome.xml` 的文件。

`WelcomeXml` 用只含 `string`、`table`、`math`、`bit32` 和 `utf8` 的 LuaJ 环境执行它，不提供 `io`、`os`、`package`、`debug`、`require` 或 Java 桥。脚本必须返回一张表，源文件不能是字节码，最大 64 KiB。

简单字段是 `background`、`icon`、`icon_size`、`gravity`、`image`、`text`、`text_size`、`text_color` 和 `text_gravity`。`text` 在打包设备上用系统字体画成 PNG，再作为 bitmap 放进窗口背景；窗口背景不能放 TextView。更复杂的画面用 `layers` 数组，每层是纯色、渐变、图标、图片或文字之一，并可设置 `shape`、`corners`、`size`、`gravity` 和 `inset`。`layers` 不能和简单字段混用。颜色只能是 `#RRGGBB` 或 `#AARRGGBB`，尺寸只能是 `dp`，图片必须是工程根目录中的小写资源文件名，例如 `welcome.png`。生成的 XML 只包含 `layer-list`、`item`、`bitmap`、`shape`、`solid`、`gradient`、`corners` 和 `color`。

`image` 会铺满启动页并忽略 `icon`。自定义图片和文字不会新增资源名。它们覆盖基础 APK 里 `res/drawable-nodpi/` 中已经存在的 `welcome_image.png` 和 `welcome_text.png`（各最多 4 张）。这些文件必须是 nodpi，否则系统会把无密度的 `drawable/` 当成 mdpi 再缩放一次。文字按 4 倍密度绘制，字体缩放固定为 1，图层宽高是像素除以 4 得到的 dp，不跟随打包手机的显示密度或字体大小。不要改 `public.xml`，APKEditor 会按这份声明重建资源表，错误的新 ID 会覆盖已有 drawable。`welcome.lua` 最多执行 100000 条指令。缺省背景是 `?colorSurface`，缺省图标是已经替换过的 `@drawable/icon`。

### Workspace 与缓存

相关文件：

- `app/src/main/java/com/nekolaska/apk/BuildWorkspace.kt`
- `app/src/main/java/com/nekolaska/apk/Editor.kt`

每次构建使用 `cacheDir/apk_builds/` 下的独立目录。`BuildWorkspace.mutex` 串行化 APKEditor 构建和清缓存，避免共享缓存被并发删除或覆盖。

反编译缓存由基础 APK canonical path、文件大小、SHA-256 和 deDex 选项共同校验。不要再次把所有构建固定到 `cacheDir/apk_editor`。

APKEditor 解包/构建与 APK 签名 API 是阻塞调用，协程取消只能在阶段之间检查。修改流水线时应在耗时阶段后及导出前保留 `ensureActive()`，防止页面离开后继续签名或覆盖已有产物。

### 导出事务

APK 与 V4 `.idsig` 被视为一组：先复制到 staging 文件，再备份旧产物，再安装新文件。任一阶段失败时应删除已安装的新文件并恢复备份。

APK 文件名必须：

- 替换文件系统非法字符。
- 限制 UTF-8 字节长度，而不是只限制 Kotlin 字符数。
- 为 `.apk`、staging 和 backup 后缀保留空间。

## 6. 签名配置与 JKS 导出

核心文件：

- `app/src/main/java/com/nekolaska/apk/MySigner.kt`
- `app/src/main/java/com/nekolaska/dialog/SignConfigDialog.kt`
- `MainActivity` 的 `CreateDocument` 导出入口

当前 JKS 私有文件：

```text
filesDir/neluaj.jks
```

签名配置保存在 SharedPreferences `sign_config` 中，包括别名、密钥密码、密钥库密码、CN、组织、国家和有效期。

行为约束：

- JKS 生成使用临时文件和原子替换。
- 签名、导出和删除共用进程内锁，避免并发读写。
- 用户实际修改签名配置后，旧 JKS 会失效；下次签名或导出时按新配置生成。
- “重新生成”强制删除旧 JKS，但生成推迟到下一次签名或导出。
- “导出签名文件”使用系统 `CreateDocument`，默认文件名为 `neluaj-signing-key.jks`。
- 导出的文件就是 Builder 当前用于 APK 签名的 JKS。密码和别名仍由签名配置界面管理，不应把密码写入导出路径或日志。

## 7. 构建完成后的安装流程

成功对话框提供“安装”和“取消”。安装流程位于 `AppProcessFragment`：

1. 验证导出的 APK 仍然存在。
2. Android 8+ 检查 `canRequestPackageInstalls()`。
3. 未授权时打开当前 Builder 的“安装未知应用”设置页，返回后自动继续。
4. 使用 `FileProvider` 生成 `content://` URI。
5. 使用 APK MIME 类型和 `FLAG_GRANT_READ_URI_PERMISSION` 调起系统安装器。

Manifest 需要保留：

- `android.permission.REQUEST_INSTALL_PACKAGES`
- authority `${applicationId}.fileprovider`
- `android:exported="false"`
- `android:grantUriPermissions="true"`

`app/src/main/res/xml/file_paths.xml` 只暴露 `LuaJ/Builds/`。不要改为暴露整个外部存储根目录，也不要使用 `file://` URI。

## 8. 关键文件索引

| 功能 | 文件 |
|---|---|
| 外部 Intent、权限、工程导航、JKS 导出 | `app/src/main/java/com/nekolaska/MainActivity.kt` |
| Builder manifest / Provider | `app/src/main/AndroidManifest.xml` |
| 工程列表和异步扫描 | `app/src/main/java/com/nekolaska/fragments/ProjectListFragment.kt` |
| 配置编辑和构建前校验 | `app/src/main/java/com/nekolaska/fragments/ProjectDetailFragment.kt` |
| 构建、签名、导出和安装 | `app/src/main/java/com/nekolaska/fragments/AppProcessFragment.kt` |
| APKEditor 调用与反编译缓存 | `app/src/main/java/com/nekolaska/apk/Editor.kt` |
| Workspace 与构建互斥 | `app/src/main/java/com/nekolaska/apk/BuildWorkspace.kt` |
| 签名与 JKS 导出 | `app/src/main/java/com/nekolaska/apk/MySigner.kt` |
| 配置模型 | `app/src/main/java/com/nekolaska/data/InitConfig.kt` |
| 配置解析 / 写回 | `app/src/main/java/com/nekolaska/ktx/io/LuaConfigParser.kt`、`app/src/main/java/com/nekolaska/data/InitConfigWriter.kt` |
| Manifest 修改 | `app/src/main/java/com/nekolaska/apk/ManifestReplacer2.kt` |
| FileProvider 路径 | `app/src/main/res/xml/file_paths.xml` |
| 主应用调用端 | `NeLuaJ+/app/src/main/assets/activities/main/Actions.lua` |

## 9. 测试与验证

在 Builder 仓库根目录运行：

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Windows PowerShell 也可使用：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

重要单元测试：

- `LuaConfigParserTest`：字面量、注释、长字符串、返回表、非法结构、计算字段。
- `InitConfigWriterTest`：精确写回、别名、注释保留、`return {}`、缺失逗号。
- `AppProcessFragmentTest`：APK 文件名清洗与 UTF-8 长度。
- `MySignerTest`：JKS 完整导出和空文件拒绝。
- `WelcomeXmlTest`：沙箱执行 `welcome.lua`、简单字段和 `layers`。

本地单元测试会给 JVM 加上 `-noverify`，因为 `luajpp.jar` 缺少 `StackMapTable`。这只影响桌面测试，不影响 Android 构建。

涉及主应用调用端时，在 `NeLuaJ+` 仓库运行：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

还应执行：

```bash
git diff --check
adb devices
```

有设备时至少手动验证：

1. 从 NeLuaJ+ 当前工程打开 Builder。
2. 权限未授予和已授予两条路径。
3. Builder 已运行时连续打开不同工程。
4. 修改并保存普通赋值和 `return {}` 两种 `init.lua`。
5. 首次构建、缓存构建、取消构建和失败恢复。
6. V1-V4 签名组合及 `.idsig` 导出。
7. 导出 JKS，并用界面中的别名/密码读取该 JKS。
8. 构建成功后首次授权未知来源并继续安装。

## 10. 后续修改检查清单

- 修改 action、extra、package 或 Activity 名称时是否同步两端和 manifest？
- 是否仍然只接受 canonical `LuaJ/Projects` 子目录？
- 是否避免执行项目 `init.lua`？
- 新增配置字段是否同时更新解析、验证、写回和测试？
- 写回是否保留未知代码、注释、别名和 `return {}`？
- 构建是否继续使用独立 workspace 和互斥锁？
- 导出失败时是否能恢复旧 APK 与 `.idsig`？
- 签名配置变化时是否正确轮换 JKS？
- FileProvider 是否仍只暴露 `LuaJ/Builds/`？
- Android 8+ 安装未知来源授权返回后是否能继续安装？
- 是否运行 Builder 的 assemble、lint、unit tests 和设备验证？

## 11. 已知限制

- split base APK 当前明确不支持。
- APKEditor、签名和部分文件操作是阻塞 API，无法在调用中途强制取消。
- 真正的跨应用跳转、系统 JKS 保存器和系统 APK 安装器必须在设备或模拟器上验证。
- Builder 依赖已安装的 NeLuaJ+ 作为基础 APK；默认基础包名可以在 Builder 菜单中修改。
- 工作树可能同时包含 APKEditor 升级或其他未提交改动，维护时不要回滚无关文件。
