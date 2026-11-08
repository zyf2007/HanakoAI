**Full Changelog**: https://github.com/zyf2007/HanakoAI/compare/v0.0.18-alpha...v0.0.19-alpha

Full 版本带有本地 ML Kit OCR 模型，Lite 版本只能使用云端 OCR 模型。
ML Kit OCR 不支持 Latex 公式识别，因此无法用于数学题目解析的场景。如果没有特殊需求，建议使用 Lite 版本。

## Changelog

- 多选题答案在悬浮球中以自适应宽度的胶囊显示，保持字母单行，过宽时自动缩小字号。
- 修复悬浮球缩小时残留圆形或方形阴影的问题；阴影跟随球体尺寸、形状和整体透明度，直径为 0 时隐藏。
- 自动模式完成通知直接显示选择题答案或已复制内容，长答案支持展开查看。
- Android 12 及以上版本默认在截屏和录屏中隐藏悬浮球、菜单及面板，并移除原 Beta 开关。

## 下载说明

99% 的手机请下载 [app-lite-arm64-v8a-release.apk](https://github.com/zyf2007/HanakoAI/releases/download/v0.0.19-alpha/app-lite-arm64-v8a-release.apk) (3.05MB)
如果你明确需要本地 ML Kit OCR，再下载 [app-full-arm64-v8a-release.apk](https://github.com/zyf2007/HanakoAI/releases/download/v0.0.19-alpha/app-full-arm64-v8a-release.apk) (16.06MB)
32 位设备可以下载：
- [app-lite-armeabi-v7a-release.apk](https://github.com/zyf2007/HanakoAI/releases/download/v0.0.19-alpha/app-lite-armeabi-v7a-release.apk) (3.05MB)
- [app-full-armeabi-v7a-release.apk](https://github.com/zyf2007/HanakoAI/releases/download/v0.0.19-alpha/app-full-armeabi-v7a-release.apk) (11.97MB)

## 说明

0.0.9 版本中加入了 The Kirari Network 在线登录的模型获取方式，但目前仍建议优先使用自己的模型 API。Kirari 这边我有时候会换模型，用之前记得到「模型提供方 → The Kirari Network」一键同步。
