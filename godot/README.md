# MusounD Godot 客户端 · 0.13.0

完整原生客户端，使用 Godot **4.7.1 标准版 / GDScript / Compatibility**。在 Godot 项目管理器中导入本目录 `project.godot`，按 F6/F5 运行；主场景为 `main.tscn`。本版本不导出或维护网页。

## 操作

启动首页 → 进入选歌 → 开始演奏或自动演示。内置 Desultory Signals 与 AT Flow，导入 `.interval` / `.zip` 后歌曲与谱面保存在本机曲库。Android 使用系统文件选择器，不需要管理全部文件的权限。导入的旧 0.11.0 与新 0.12.0 格式均可读取，制谱器可以继续使用现有版本。

Tap 在到线时点击，Hold 持续按住并跟随底部交点；点击高度由当前二维判定区域限制。区域取并集，交叠内部边界隐藏；线位置、透明度、独立流速、Hold 弯折与区域平移 / 旋转 / 伸缩全部保留。判定线固定在下方，Tap 与 Hold 的各段竖直下落。

支持 16:9、4:3、超宽屏居中、多点触控、鼠标；电脑另支持 D/F/J/K 四键。空格暂停或继续，Esc / Android 返回键返回上一层。选歌可用左右翻页按钮或横向滑动。离开应用或失去焦点自动暂停，继续前倒数三秒，可提前重新按住 Hold。

设置：下落时间 0.6–4.0 秒、延迟补偿 ±300 ms、区域填色、打击音开关和 0–200% 音量。独立音效播放器支持多押重叠发声。保存本机成绩，自动演示不记纪录；结算有判定数量、分数、准确率、最大连击、评级与平均时差。

## 格式与旧数据

保留格式 v6、coordinateScale=100，读取 v1–v5 时转换旧归一化坐标；基线兼容测试覆盖 Composer 0.11.0，早期读取分支也保留。未知格式和要求更高 minReaderVersion 的文件明确拒绝。客户端为 0.13.0，谱面不需要改版；现有制谱器与 APK 0.12.0 均可继续使用原包。

Android 包名继续为 `studio.interval`。使用与旧开发版相同签名时可覆盖安装，保留 `files/charts` 曲库，并读取旧 SharedPreferences 设置与最高分。不要先卸载旧 APK，卸载可能删除应用数据。Windows 默认保存到 `%APPDATA%/MusounD/`；Android 保存在应用私有 files 目录。

## 结构

- `core/rhythm_core.gd`：纯判定、Tap/Hold、曲线积分、轨迹与区域几何。
- `core/chart_loader.gd`：版本、坐标、数据校验和兼容。
- `core/region_outline.gd`：区域并集外边缘。
- `services/library.gd`：曲库、ZIP 包检查、后台导入、音频读取。
- `services/audio_clock.gd`：音乐时钟、输出延迟补偿、多声音效。
- `services/profile.gd`：设置、成绩与旧 Android 数据读取。
- `ui/playfield.gd`：游戏绘制与屏幕坐标转换。
- `main.gd` / `main.tscn`：页面、触控、生命周期与流程。

## 构建和测试

独立 Godot 工程包可直接在编辑器中运行，并通过「项目 → 导出」生成客户端。以下脚本命令适用于 Git 仓库的完整源码，在上一级工程根目录执行：

```powershell
python tools/build_godot.py
```

可指定 `--godot <Godot EXE>`、`--target Windows` 或 `--target Android`。需要对应的 4.7.1 导出模板；Android 还需要 JDK 和 SDK。Android Alpha 构建使用本机 `build/debug.jks`，私钥不会提交 Git。

核心测试使用 Godot 无界面运行 `--headless --path godot --script tests/core_tests.gd`。运行客户端时可传 `-- --verify-output=<报告绝对路径>` 检查实际界面、声音、触控与暂停续接。`tools/test_godot_android.py` 仅用于隔离模拟器，会写入测试曲库与成绩。

导出目录：`dist/godot-0.13.0/`（EXE 与 PCK 必须一起保留）；APK：`dist/MusounD-0.13.0.apk`。

## 许可

Godot Engine 使用 MIT 许可，随工程附带 `licenses/Godot-LICENSE.txt` 和 `licenses/Godot-COPYRIGHT.txt`，另见 https://godotengine.org/license/ 。Noto Sans SC 使用 SIL OFL，随字体附带 `assets/fonts/OFL.txt`。示例音乐版权归 technoplanet；资源沿用现有客户端示例。Godot 本机安装、导出模板与 Android 签名不纳入源码仓库。
