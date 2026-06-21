# Hanako

Hanako 是一个 Android 悬浮窗 AI 客户端，内置提示词支持的核心用途是搜题与快速作答，你也可以通过配置助手提示词配置成其他功能。  

[[Download 0.0.8-alpha](https://github.com/zyf2007/HanakoAI/releases/tag/v0.0.8-alpha)]  

Hanako 把截图、识题、解题、复制/填写这几个步骤压缩到尽可能短：

- 普通模式：框选题目区域后发给 AI，展示解题思路，并附带可一键复制的答案片段，方便粘贴填写。
- 自动模式：不需要框选，直接整屏发送给 AI；选择题答案会显示在悬浮球里，填空题或文本答案会自动写入剪贴板，用户只需要点击或粘贴即可，每题基本只要一步。
  - 单图模式：单击悬浮球直接将当前屏幕发给 AI。
  - 多图模式：长按悬浮球进入多图模式，之后每次单击悬浮球会往缓冲区中添加一张截图，直到再次长按一起发给 AI。中途如果误触可以双击取消。

## 主要功能

- 悬浮球常驻桌面，随时发起识题
- 支持普通模式与自动模式
- 支持两种处理链路：
  - `OCR_THEN_LLM`：先 OCR 提取文字，再交给文本模型分析，适合纯文本题目提高准确度/降低成本。
  - `MULTIMODAL_DIRECT`：直接把截图交给多模态模型理解，适合带有图片的题目。
- 普通模式支持输出解题思路、关键知识点和答案
- 普通模式支持 `[copy:内容]` 片段，一键复制后直接填写
- 自动模式支持：
  - 选择题：把 `A` / `BC` / `ABD` 这类字母答案写到悬浮球里
  - 填空题/简答题：把最终答案写入系统剪贴板
- 支持历史记录查看
- 支持自定义模型提供方、模型和助手提示词

## 模式说明

### 普通模式

普通模式适合希望先看分析过程、再决定如何填写答案的场景。

使用流程：

1. 启动悬浮球。
2. 点击悬浮球。
3. 框选题目区域。
4. 将截图发送给 AI。
5. 在面板里查看解题思路、答案，以及一键复制片段。

这个模式更适合：

- 需要看解题过程
- 需要核对 OCR 结果
- 不是单纯客观题，答案需要自行整理后填写

### 自动模式

自动模式适合追求极短操作链路的场景。

使用流程：

1. 在主页长按“启动”进入自动模式。
2. 点击悬浮球。
3. 应用直接截取整张屏幕并发送给 AI。
4. AI 判断题型并只执行一个动作：
   - 选择题：把答案字母显示到悬浮球
   - 填空题/文本题：把最终答案写入剪贴板

这个模式的目标是：

- 不用框选
- 不用弹出任何窗口
- 每题尽量只保留一次点击或一次粘贴

## 悬浮球操作说明

悬浮球启动后会常驻在屏幕上，可以自由拖动到任意位置。不同手势对应不同操作：

| 手势 | Idle / Copied / Error | Processing | MultiPage | MenuExpanded |
|------|-----------------------|------------|-----------|--------------|
| 单击 | 打开截屏面板 | 打开截屏面板 | 截图一页 | 关闭菜单 |
| 双击 | 展开扇形菜单 | 取消处理 | 退出多图 | 关闭菜单 |
| 长按 | 进入多图截图 | 展开扇形菜单 | 发送截图 | 关闭菜单 |
| 拖动 | 移动气泡 | 移动气泡 | 移动气泡 | — |

### 快捷菜单

双击悬浮球会展开一个扇形快捷菜单，提供以下功能：

- **视觉**：切换 OCR 模式 / 多模态模式（高亮表示当前选中的模式）
- **设置**：打开应用主界面进行配置

菜单会根据气泡在屏幕上的位置自动调整展开方向，确保在屏幕边缘也能完整显示。点击菜单外的空白区域或再次双击可关闭菜单。

### 多图截图模式

长按悬浮球进入多图模式后，气泡会变红并显示已截取的数量：

- 单击：截取当前屏幕并添加到缓冲区
- 长按：将所有截图一起发送给 AI
- 双击：取消多图模式并清空缓冲区

## 权限说明

应用依赖以下系统能力：

- 悬浮窗权限：用于显示悬浮球和结果面板
- 截屏/录屏授权：用于抓取屏幕内容发送给 AI
- 网络权限：用于请求模型接口
- 通知权限：用于自动模式完成后的通知提醒

Android 14 及以上设备，首次启动相关功能时会看到系统截屏授权弹窗。

## 配置说明

应用内可以直接配置：

- 模型提供方
- API Base URL
- API Key
- OCR 模型
- 文本模型
- 多模态模型
- 助手提示词

当前代码内已支持的提供方类型：

- OpenAI Compatible
- OpenAI Responses
- Anthropic
- Google Gemini

你也可以分别给 `OCR`、`文本`、`多模态` 指定不同提供方和模型。

## 技术实现概览

- Android + Kotlin
- Jetpack Compose
- DataStore 保存本地配置与历史记录
- OkHttp / SSE 处理流式模型输出
- 前台服务 + MediaProjection 完成悬浮窗与截屏

主要目录：

- [app/src/main/java/fun/kirari/hanako/ui](/home/zyf/Code/Projects/Hanako/app/src/main/java/fun/kirari/hanako/ui)
- [app/src/main/java/fun/kirari/hanako/overlay](/home/zyf/Code/Projects/Hanako/app/src/main/java/fun/kirari/hanako/overlay)
- [app/src/main/java/fun/kirari/hanako/capture](/home/zyf/Code/Projects/Hanako/app/src/main/java/fun/kirari/hanako/capture)
- [app/src/main/java/fun/kirari/hanako/network](/home/zyf/Code/Projects/Hanako/app/src/main/java/fun/kirari/hanako/network)
- [app/src/main/java/fun/kirari/hanako/automation](/home/zyf/Code/Projects/Hanako/app/src/main/java/fun/kirari/hanako/automation)

## 本地构建

环境要求：

- Android Studio 最新稳定版
- Android SDK 36
- JDK 11
- Android 12 及以上设备或模拟器（项目 `minSdk = 31`）

构建调试包：

```bash
./gradlew assembleDebug
```

运行单元测试：

```bash
./gradlew test
```

如果需要构建签名发布包，可在项目根目录提供 `keystore.properties`。


