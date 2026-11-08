# Hanako

Hanako 是一个 Android 悬浮窗 AI 客户端，核心用途是截图识题与快速作答。默认助手提示词偏向搜题场景，也可以在应用内改成翻译、总结、阅读辅助等其他用途。

[[Download 0.0.18-alpha](https://github.com/zyf2007/HanakoAI/releases/download/v0.0.18-alpha/app-lite-arm64-v8a-release.apk)]  [[View Release Notes](https://github.com/zyf2007/HanakoAI/releases/tag/v0.0.18-alpha)] [[Telegram](https://t.me/hutao_space)]

Hanako 把截图、识题、解题、复制/填写这几个步骤压缩到尽可能短：

- 普通模式：点击悬浮球后框选题目区域，发送给 AI，结果面板会展示解题思路、答案和可一键复制的答案片段。
- 自动模式：主页长按启动后进入自动模式，点击悬浮球会直接截取整屏并交给 AI；选择题答案显示在悬浮球里，填空题或文本答案写入剪贴板。
- 多图模式：长按悬浮球进入多图截图，每次单击添加一页，再次长按把多张截图一起发送给 AI，双击取消。

## 主要功能

- 悬浮球常驻桌面，支持拖动、点击、双击、长按和扇形快捷菜单。
- Android 12 及以上版本默认在截屏和录屏中隐藏悬浮球、菜单及面板，无需设置开关。
- 支持普通模式、自动模式、多图截图模式。
- 支持两种处理链路：
  - `OCR_THEN_LLM`：先 OCR 提取文字，再交给文本模型分析，适合纯文本题目。
  - `MULTIMODAL_DIRECT`：直接把截图交给多模态模型理解，适合含图题目。
- 支持云端 OCR、文本模型、多模态模型分别选择不同提供方和模型。
- Full 版本内置 ML Kit 中文 OCR；Lite 版本不包含本地 OCR，建议使用云端 OCR 模型。
- 普通模式支持 Markdown / LaTeX 渲染、答案版本切换、`[copy:内容]` 一键复制片段。
- 自动模式支持选择题字母答案显示、文本答案剪贴板写入、包含答案的完成通知（长答案可展开查看）、超时设置和静态振动提示。
- 支持历史记录查看、删除、重新生成，并把截图保存到应用私有文件。
- 支持联网搜索工具，由模型按需调用 `web_search` 后把搜索结果注入上下文。
- 支持应用内检查更新，在首页标题旁提示新版本。
- 支持 The Kirari Network OIDC 登录和 Kirari LLM 网关，也支持自定义模型提供方。

## 使用方式

### 普通模式

普通模式适合希望先看分析过程、再决定如何填写答案的场景。

1. 在 Hanako 首页启动悬浮球。
2. 点击悬浮球。
3. 框选题目区域。
4. 将截图发送给 AI。
5. 在结果面板里查看解题思路、答案和一键复制片段。

### 自动模式

自动模式适合追求极短操作链路的场景。

1. 在 Hanako 首页长按启动按钮进入自动模式。
2. 点击悬浮球。
3. 应用截取整张屏幕并发送给 AI。
4. AI 判断题型并执行一个动作：
   - 选择题：把 `A` / `BC` / `ABD` 这类字母答案显示到悬浮球。
   - 填空题/文本题：把最终答案写入系统剪贴板。

## 悬浮球操作

悬浮球启动后会常驻在屏幕上，可以自由拖动到任意位置。不同状态下的手势含义如下：

| 手势 | Idle / Copied / Error | Processing | MultiPage | MenuExpanded |
|------|-----------------------|------------|-----------|--------------|
| 单击 | 打开截屏面板 | 打开截屏面板 | 截图一页 | 关闭菜单 |
| 双击 | 展开扇形菜单 | 取消处理 | 退出多图 | 关闭菜单 |
| 长按 | 进入多图截图 | 展开扇形菜单 | 发送截图 | 关闭菜单 |
| 拖动 | 移动气泡 | 移动气泡 | 移动气泡 | - |

### 快捷菜单

双击悬浮球会展开扇形快捷菜单：

- **视觉**：切换 OCR 模式 / 多模态模式，高亮表示当前为多模态模式。
- **联网**：开启或关闭联网搜索，高亮表示已开启。
- **设置**：打开应用主界面。

菜单会根据气泡在屏幕上的位置自动调整展开方向。可以在「设置 → 更多 → 扇形菜单设置」关闭菜单或调整跟随尺寸。

悬浮球阴影跟随球体直径和整体透明度实时调整；球体直径设为 0 时隐藏阴影。

### 多图截图模式

长按悬浮球进入多图模式后，气泡会显示已截取数量：

- 单击：截取当前屏幕并添加到缓冲区。
- 长按：将所有截图一起发送给 AI。
- 双击：取消多图模式并清空缓冲区。

## 权限与截图方式

应用依赖以下系统能力：

- 悬浮窗权限：用于显示悬浮球和结果面板。
- 网络权限：用于请求模型接口、搜索接口和检查更新。
- 通知权限：用于自动模式完成后的通知提醒。
- 截屏能力：可在「设置 → 更多 → 屏幕录制方式」选择。

当前支持两种截图后端：

- MediaProjection：Android 系统截屏/录屏授权，兼容性最好，但通常会出现系统授权弹窗。
- Shizuku ADB：通过 Shizuku 执行 `screencap`，需要设备已运行并授权 Shizuku。

## 配置说明

应用主界面底部有两个入口：

- **Hanako**：启动悬浮球、进入自动模式、切换处理链路、查看历史记录。
- **设置**：管理模型、联网搜索、助手和其他高级选项。

设置页目前分为：

- **模型提供方**：新增/编辑 API Base URL、API Key、提供方类型，支持连接测试和模型列表预览。
- **模型设置**：分别为 OCR、文本、多模态任务指定提供方和模型，可收藏常用模型或手动输入模型名。
- **联网搜索**：配置搜索开关、自动模式是否允许搜索、搜索引擎、API URL、API Key 和 Tavily 额度查询。
- **助手配置**：管理助手名称、OCR 提示词、文本提示词和多模态提示词。
- **更多**：配置悬浮球外观、扇形菜单、自动模式、静态振动、截图方式、网络兼容和 The Kirari Network。

当前支持的模型提供方类型：

- OpenAI Compatible
- OpenAI Responses
- Anthropic
- Google Gemini
- The Kirari Network

## 联网搜索

联网搜索开启后，当前任务模型可以通过 `web_search` 工具决定是否需要搜索。若模型调用工具，Hanako 会根据工具参数中的 `query` 调用搜索引擎，并把结果作为上下文继续交给模型生成回答。

适用场景：

- 时政、新闻、近期事件等模型训练数据未覆盖的问题。
- 需要最新数据才能准确作答的题目。
- 明确要求联网查询的问题。

支持的搜索引擎：

- Tavily
- Brave Search
- Serper.dev
- 自定义（Tavily 兼容格式）

## 技术实现概览

- Android + Kotlin
- Jetpack Compose + Material 3
- DataStore 保存本地配置与历史记录
- OkHttp / SSE 处理流式模型输出
- MediaProjection / Shizuku 完成截屏
- 前台服务承载悬浮窗
- `kirari-llm-core` 封装多提供方 LLM 适配器与工具调用格式
- `kirari-auth-core` 封装 OIDC PKCE 登录流程

主要目录：

- [app/src/main/java/fun/kirari/hanako/ui](app/src/main/java/fun/kirari/hanako/ui)
- [app/src/main/java/fun/kirari/hanako/overlay](app/src/main/java/fun/kirari/hanako/overlay)
- [app/src/main/java/fun/kirari/hanako/capture](app/src/main/java/fun/kirari/hanako/capture)
- [app/src/main/java/fun/kirari/hanako/network](app/src/main/java/fun/kirari/hanako/network)
- [app/src/main/java/fun/kirari/hanako/workflow](app/src/main/java/fun/kirari/hanako/workflow)
- [kirari-llm-core](kirari-llm-core)
- [kirari-auth-core](kirari-auth-core)

## 本地构建

环境要求：

- Android Studio 最新稳定版
- Android SDK 36
- JDK 11
- Android 7.0 及以上设备或模拟器（项目 `minSdk = 24`）

构建调试包：

```bash
./gradlew assembleDebug
```

运行单元测试：

```bash
./gradlew test
```

构建 release 包：

```bash
./gradlew assembleRelease
```

项目包含 `full` 和 `lite` 两个 flavor，并按 ABI 拆包：

- `app-lite-arm64-v8a-release.apk`：推荐大多数手机使用。
- `app-full-arm64-v8a-release.apk`：包含本地 ML Kit 中文 OCR。
- `app-lite-armeabi-v7a-release.apk` / `app-full-armeabi-v7a-release.apk`：32 位设备使用。

如果需要构建签名发布包，可在项目根目录提供 `keystore.properties`。

## Changelog

- 历史记录支持长按或点击已引用内容块，快速定位并查看对应题目与回答。
- 新增 Markdown 内容块引用流程，可在追问中携带多个引用片段，并显示引用关系。
- 引用菜单新增复制、引用和关闭操作，适配数学公式内容的尺寸与预览定位。
- 统一历史记录与模型提供方的数据存储权威，补充答案版本 ID 迁移和架构回归测试。
