# Godot 客户端迁移 · 0.13.0

客户端主实现已迁至 `godot/`，使用本机 Godot 4.7.1、GDScript 和 Compatibility 渲染器。主项目为 `godot/project.godot`，场景为 `main.tscn`。根目录默认构建入口已切换为 Godot；旧 Java 客户端保留为参考与回归基线，`build.ps1 -Legacy` 可构建旧客户端。本次不修改网页端。

## 功能对照

| 原客户端功能 | Godot 实现 |
| --- | --- |
| 首页 / 选歌 / 分页与滑动 / 歌曲详情 | `main.gd` 与 Godot Control、Button |
| 内置两首谱面 / 导入曲库 | `services/library.gd`，后台 ZIP 导入、系统文件选择器 |
| Tap / Hold / 多点 / 时间窗 / 判定与计分 | `core/rhythm_core.gd`，保留 60/120/160 ms 时间窗与 85 ms 离区宽限 |
| 固定判定线、弯折轨迹与 Hold 竖直下落 | `ui/playfield.gd`；绝对到达时间决定各段横坐标 |
| 线位置 / 透明度 / 独立流速 | 曲线与速度积分，支持所有既有缓动 |
| 多个二维区域、平移 / 旋转 / 长宽 | 同一几何模型，分别按屏幕比例旋转 |
| 重叠区域隐藏内部边缘 | 多边形边切分求并集外轮廓 |
| 16:9 / 4:3 / 超宽屏 | 逻辑区域自适应，超宽居中；多点坐标共用渲染变换 |
| 音乐同步 / 暂停 / 后台 / 倒数续接 | 音频播放位置、混音间隔插值与输出延迟补偿；暂停冻结时钟 |
| 打击音效 / 双押重叠 | 24 个独立 AudioStreamPlayer 声部 |
| 流速、补偿、区域辅助设置 | 0.6–4.0 秒、±300 ms、填色开关；另加打击音量 |
| 结算 / 评级 / 本地最高分 | 结果页、判定数量、准确率、最大连击、平均时差；演示不记分 |
| 旧谱面兼容与版本提示 | v1–v6 读取；基线兼容 Composer 0.11.0；未知版本明确拒绝 |
| Android 旧数据迁移 | 包名和签名一致覆盖安装，保留曲库，通过原生 SharedPreferences 读取设置和成绩 |

## 兼容与导出

客户端版本为 0.13.0，Android versionCode=13；谱面仍为 v6、百分制空间坐标。制谱器继续使用现有 0.12.0 修正版，不必重新制作谱面。新客户端不要求改变谱面内容。

Android APK 保留 `studio.interval` 包名及原开发签名。覆盖安装可以保留已有歌曲，原本内置 AT Flow 的成绩按旧路径兼容；旧导入谱面成绩按 Java 路径 hash 读取，并兼容 `/data/data/` 与 `/data/user/0/` 路径别名。不要先卸载旧应用。

Godot Compatibility 需要 OpenGL ES 3.0（Android）。当前导出模板最低 API 24，已验证 Android 10 模拟器；实际手机的触控与音频延迟仍需设备校准。桌面产物为 EXE + PCK，必须放在同一目录。

## 验证

- 189 项核心检查通过：两首完整曲目在两种比例及 30/60/120 FPS 下均满分；判定、Hold、边界、重接、版本拒绝、旧坐标、Python 几何对照与速度积分。
- 桌面实际运行检查：选歌、设置、音乐时钟、双指输入、Hold、暂停 / 倒数续接、重开与结算；保存 16:9 / 4:3 图像。
- Windows 导出产物单独运行诊断，确认打包后的内置歌曲和脚本可读取。
- Android 使用系统 `content://` 文件 URI 导入真实 711 音符 AT Flow；验证从旧客户端写入的曲库、设置、内置曲成绩和导入曲成绩覆盖升级后可读取。
- Android 16:9 与 4:3 下进行实际播放、双指输入、Hold 与暂停续接检查。

测试使用隔离的模拟器，未把模拟器测试声称为真机验证。检查脚本和结果见 `godot/tests/`、`godot/services/diagnostics.gd`、`tests/godot_android/` 与发布验证 JSON。

Godot 官方文档参考：[Android 导出](https://docs.godotengine.org/en/stable/tutorials/export/exporting_for_android.html)、[音频同步](https://docs.godotengine.org/en/stable/tutorials/audio/sync_with_audio.html)、[Java/Android 原生接口](https://docs.godotengine.org/en/stable/tutorials/platform/android/javaclasswrapper_and_androidruntimeplugin.html)。
