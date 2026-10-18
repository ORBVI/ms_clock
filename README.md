# HS / SYNC · 四相机同步测试靶

原生 Android 全屏计时器。让四个相机同时拍摄**同一块屏幕**，通过累计时间、横向运动扫描条和绘制序号，人工逐帧比较采集时间差。应用打开后默认进入黑底红字极简模式，自动计时并保持屏幕常亮，无需网络和相机权限。

## 极简展示模式

专门用于现场展示、视频宣传：全屏仅显示大号 `分:秒.毫秒`，去掉小时、标题、扫描条、编码和状态文案。数字根据屏幕宽高自适应放大，占可用宽度约 94%，固定字符位置，避免数字变化时左右抖动。分钟累计显示，超过 59 分钟不回零。

点击画面显示控制栏，可切换红字 / 白字；「测试靶」切回完整同步靶，「极简」切回数字并自动隐藏控制栏。「全屏」只隐藏控制栏，不改变当前模式。横屏展示数字最大。

## 操作

- **暂停 / 继续**：冻结或继续整张测试靶；暂停期间不累计时间。
- **归零**：清零累计时间、绘制序号和刷新统计，保持当前运行 / 暂停状态。
- **浅色 / 深色**：切换拍摄靶极性；对比标记始终使用高对比黑白。
- **全屏**：隐藏底部控制栏，点击靶面恢复。横屏可得到最大的时间数字。
- 横竖屏切换保留计时和序号；退到后台不绘制，但运行中的累计时间继续。重新启动一个新会话从零开始。

## 怎样比较四路采集

1. 固定手机，让四路相机同时看到完整靶面。选择合适的屏幕亮度与相机曝光，避免数字过曝、运动条拖影。
2. 四路同时录制，按已有硬件触发序号 / 时间戳选取应该对应的帧。
3. 读取四张图中**相同屏幕高度**的累计时间，计算相对一路参考相机的读数差，并记录 `max(t) - min(t)`。
4. 绘制序号一致、扫描条位置一致可辅助确认显示状态。16 位 Gray 编码从左至右为高位到低位，实心为 1、空心为 0；编码对应 `sequence XOR (sequence >> 1)` 的低 16 位，完整序号以数字为准。
5. 跨多组帧统计分布，避免只用一张图判定同步。

## 测量边界

- 显示时间格式是 `分:秒.毫秒`，使用 Android 单调时间源；不是 UTC 或与相机对时后的硬件时间。
- 全部标记在同一个 Canvas 绘制中使用同一组状态。绘制序号是应用回调更新序号，**不是相机帧号，也不是经过面板确认的物理显示帧号**。
- 底部 Hz / ms 是 Choreographer 回调间隔的平滑估计，不代表实际面板测量。60 Hz 屏幕通常约每 16.7 ms 更新一次；显示三位毫秒不意味着有 1 ms 的光学时间分辨率。
- 同一显示画面不代表曝光严格同时。刷新扫描、相机 rolling shutter、曝光积分及显示队列延迟都可能造成混合数字或条带。尽量比较相同位置；亚毫秒验收仍需硬件触发、时间戳或专用光学测试设备。
- 扫描条每秒循环，编码低位也会循环，不能脱离完整累计时间与序号单独判断长时间对应关系。
- 本应用提供拍摄靶，不负责连接四个相机或自动分析视频。

## 构建

Android SDK 34，JDK 17 或兼容的 JDK 21，Gradle Wrapper 8.5：

```sh
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.huanshi.timesync/.MainActivity
```

`local.properties` 配置本机 SDK 路径。首次构建需要能访问项目中配置的 Maven 仓库，缓存完整时可加 `--offline`。

## GitHub CI 与发布

GitHub 仓库：<https://github.com/ORBVI/ms_clock>（私有）。

- `master` 推送、Pull Request 和手动触发：在 Ubuntu / JDK 17 上构建 Debug、Release，运行两种变体的 Lint 和现有单元测试任务，保留 APK 与报告 14 天。目前仓库尚无单元测试用例，测试任务会显示 `NO-SOURCE`。
- `v*` 标签：校验标签与 `versionName`、版本说明一致；构建成功后，使用仓库签名 Secrets 签名并验证 APK，发布 GitHub Release，附 APK、SHA-256 校验文件、证书指纹。
- 发布签名 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_PASSWORD`、`ANDROID_KEY_ALIAS`。缺少任何一项时发布失败，不使用临时 debug 签名替代。
- 每次发布修改 `app/build.gradle` 的 `versionName`，递增 `versionCode`，添加 `docs/releases/vX.Y.Z.md`，待分支 CI 通过后创建并推送对应标签。
- 签名密钥及密码不得提交 Git；本地 `.release-signing/` 已被忽略，需由维护者另行安全备份。后续 APK 更新必须沿用相同签名。
- CI 使用官方 Google / Maven Central 仓库；本地保留原有镜像仓库以兼容已有缓存。

```sh
git tag -a v1.0.0 -m 'HS / SYNC v1.0.0'
git push github master
git push github v1.0.0
```
