# Godot 客户端 Agent 交接说明

核对日期：2026-11-02。适用范围：本目录的 Windows / Android 原生游戏客户端，以及相关构建、兼容、联调和发布工作。先遵循用户当前任务；本文用于延续已有实现和构建知识，不是要求自动重构或自动发布。

## 开始工作

1. 阅读本文、`README.md`、`../release.json`、`../docs/godot-migration.md`。格式变更另读 `../docs/versioning.md`。
2. 运行 `git status --short` 并检查相关 diff，保留用户改动。Godot 编辑器可能重排 `project.godot`，也可能改变实际设置；逐项核对，不能直接恢复整个文件。
3. 以下命令默认从包含 `godot/` 的源码根目录执行。本机根目录为 `C:\Users\Lenovo\Desktop\music game`。若收到的是独立 Godot 工程 ZIP，则 `project.godot` 位于解压根目录，不能假定父目录存在完整仓库的 `tools/`、`app/` 或 `editor/`。
4. 先复现问题和定位代码，完成已有授权范围内的实现及验证。缺少本机 SDK、模板或样本时明确说明缺少什么，不用空壳/旧产物替代成功构建。

## 当前架构与版本

| 项目 | 当前状态 |
| --- | --- |
| 活跃客户端 | Godot 4.7.1 标准版、GDScript、Compatibility 渲染器 |
| 程序版本 | 0.14.0；Android versionCode=14 |
| 主入口 | `project.godot` → `main.tscn` → `main.gd` |
| 文件格式 | v6，公共空间坐标 0–100，最低读取版本 0.12.0 |
| 配套制谱器 | Python/PySide6 0.12.0 检查修正版，见 `../editor/AGENTS.md` |
| 客户端仓库 | <https://github.com/ly10yue/MusounD_alpha> |

`../app/` 中的 Java/Canvas 客户端是旧版回归基线和共享示例资源来源。当前默认 `../build.ps1` 构建 Godot，`-Legacy` 才构建旧 Java 客户端。不要把修复只写进 Java 后声称已修复当前客户端。网页端不在当前维护范围。

0.13.0 迁移已完成并发布；后续以当前用户需求继续，不需要从零重做迁移。程序版本、谱面结构版本和制谱器版本不必相同。

## 源码地图

| 文件 | 职责 |
| --- | --- |
| `main.gd`、`main.tscn` | 首页、选歌、设置、导入、倒数、输入、暂停续接、结算、生命周期 |
| `core/rhythm_core.gd` | 无场景依赖的判定引擎、音符状态、轨迹积分与区域几何；内部类型为 `EventCurve` / `Track` / `Zone`，脚本实例负责判定流程 |
| `core/easing.gd` | 缓动与积分，供内部曲线类型显式引用 |
| `core/chart_loader.gd` | JSON 校验、旧格式兼容、空间单位转换及限制 |
| `core/region_outline.gd` | 多区域并集外轮廓，裁掉交叠内部边缘 |
| `ui/playfield.gd` | 画面、自适应视口、轨迹/Hold 绘制、HUD 与打击反馈 |
| `services/audio_clock.gd` | 音乐位置插值、输出延迟补偿、暂停、独立打击音声部 |
| `services/library.gd` | 内置/导入曲库、ZIP 检查、`content://` 导入、音频读取 |
| `services/profile.gd` | 配置/成绩、Java hash、旧 Android SharedPreferences 迁移 |
| `services/diagnostics.gd` | 实际场景、音频、触控及暂停流程的诊断与截图 |
| `tests/core_tests.gd`、`tests/fixtures/` | 核心回归、旧坐标与跨语言区域几何对照 |
| `assets/`、`licenses/` | 示例谱面、音乐、音效、中文字体、第三方许可 |
| `export_presets.cfg` | Windows / Android 导出预设 |
| `../tools/build_godot.py` | 完整仓库的资源同步、构建与日志检查 |
| `../tools/build_godot_tests.py`、`../tools/test_godot_android.py` | Android 测试夹具打包、旧版数据迁移与双比例联调 |

## 核心玩法约束

- 只有 Tap 和 Hold；固定判定线在下方。Tap 的横坐标取绑定线在音符到达时间的位置，下落过程中不沿线横向走动。轨迹与 Hold 可弯折，但同一绝对时间位置的各段保持竖直下落。
- Hold 头判定后，持续判定使用轨迹与下方判定线当前交点，玩家需横向跟随；手指 Y 只由当前二维区域限制。不要把“全屏垂直判定”错误实现为忽略矩形的上下边界。
- 区域按当前时间、屏幕比例计算平移/旋转/尺寸，多个区域取并集；内部边缘隐藏，实际可判定范围与绘制保持一致。
- 轨迹位置、透明度、流速事件独立。流速只改视觉距离，通过积分计算，不能改判定时刻、BPM 或音乐速率。保留 `linear`、`smooth`、`hold`、`sine_in_out`、`sine_in`、`sine_out`。
- 维持现有时间窗：Perfect 60 ms、Good 120 ms、Miss 160 ms、Hold 尾部 80 ms、离区宽限 85 ms；横向半径 0.085。修改平衡参数须有当前任务依据及测试。
- 支持 16:9 / 4:3，超宽屏居中；触控、鼠标、D/F/J/K 共用一致的判定入口。渲染逻辑区域和输入坐标转换必须一致，不硬编码某一设备像素。
- 保留触摸取消/抬起的区别：系统取消不能奖励 Hold 尾判。后台/失焦自动暂停，恢复有 3 秒倒数并可重新接 Hold；不能在暂停期间推进判定或丢失所有进行中的 Hold。
- 曲库导入、设置、选歌记录、计分、结算、最高分和自动演示都属于已有功能；自动演示不记最高分。
- 下落时间范围 0.6–4.0 秒、补偿 ±300 ms、音效音量 0–200%。最小时间 0.6 秒代表最快下落，不要弄反。

## 格式、导入和持久化

现代谱面音符保存 `id/type/time/line/duration`：time、duration 为秒，line 引用轨迹 ID。实际音频判定时刻为 `time+offset`；不要把 offset 双算。`chart_loader.gd` 将输入转换为内部结构，不应改写导入原文件。

v6 必须声明 `coordinateScale=100`；运行时空间归一化为 0–1。旧 v1–v5 走已有兼容分支，基线覆盖 Composer 0.11.0。仅转换位置/矩形/位移/中心/宽高；时间、BPM、角度、透明度、倍率不缩放。未知结构版本、混合单位、过高 `minReaderVersion` 明确拒绝，不按数值猜单位。

保持规模上限和 ZIP 文件名/大小检查；`.interval` 包根目录为 `chart.json` 与音频。Android 选择器返回的 `content://` 必须先复制为本机缓存，再交给 ZIPReader。导入失败仅清理本次创建的目录，不能删除既有曲库。扫描与导入在后台处理，UI 更新返回主线程。

内置 `res://` 音频/字体可能被导出为 remap 资源，应使用 `ResourceLoader.exists` / `load`；不能只用 `FileAccess.file_exists` 判断。外部歌曲 Ogg/MP3/WAV 使用相应 `load_from_file`；制谱器默认先转 Ogg。

| 数据 | 位置或兼容规则 |
| --- | --- |
| Windows 用户目录 | `%APPDATA%/MusounD/` |
| 新设置与成绩 | `user://profile.cfg` |
| 导入曲库 | `user://charts/<唯一目录>/chart.json` 与音频 |
| Android 包名 | `studio.interval`，用于覆盖升级与数据保留 |
| 旧设置/成绩 | 通过 AndroidRuntime 原生 SharedPreferences 读取 `interval`；选歌记录读取 `MainActivity` |
| 旧导入曲成绩 | 保留 Java UTF-16 `String.hashCode()`，兼容 `/data/data/` 和 `/data/user/0/` 路径别名 |

不要用直接读 shared_prefs XML 替代原生读取，也不要为测试先卸载用户 APK。覆盖升级必须保持包名和原签名；缺少原签名重新生成的开发 key 不能保证覆盖旧安装。

## 音频、输入与几何的已知坑

- 使用音乐播放位置 + 距最近混音的时间 − 输出延迟，保证时钟单调；暂停冻结，续接解除 `stream_paused`。音乐结束后仍需处理合法的尾部判定，不能立即截断结算。
- 24 个独立音效播放器允许双押/密集打击重叠，不能改为等待上一声播放完毕。
- UI 容器的 mouse_filter 与触摸/鼠标模拟设置会影响 `_unhandled_input`：检查事件是否被控件截走或被模拟两次。实际输入已由 Godot 变换；诊断中的 `Input.parse_input_event` 则需按 viewport stretch 转回原生窗口坐标。
- 小浮点叉积必须用 `signf`，`signi` 会截断并破坏区域并集边界。边缘切分的容差应考虑 Vector2 精度，并检查重复矩形和孔洞。
- 轨迹使用连续 polyline；逐小段分别画抗锯齿线会出现虚点接缝。Hold 的形状/裁切与轨迹使用同一计算。
- 内部曲线类命名为 `EventCurve`，避免冲突 Godot 自带 `Curve`；内部类调用缓动帮助类时使用明确的 preload 引用。

## 本机 Godot 与构建

用户给出的 `C:\Users\Lenovo\Desktop\Godot_v4.7.1-stable_win64.exe` 实际是一个目录。真正可执行文件为：

```text
C:\Users\Lenovo\Desktop\Godot_v4.7.1-stable_win64.exe\Godot_v4.7.1-stable_win64.exe
```

完整仓库构建：

```powershell
Set-Location 'C:\Users\Lenovo\Desktop\music game'
python tools/build_godot.py --target Windows
python tools/build_godot.py --target Android
# 或同时导出两个目标：
powershell -ExecutionPolicy Bypass -File build.ps1
```

可传 `--godot <实际EXE>`；独立工程包在 Godot 中导入 `project.godot`、按 F5 运行，通过「项目 → 导出」使用预设。需要与引擎匹配的 Windows/Android 导出模板，位于 `%APPDATA%/Godot/export_templates/4.7.1.stable/`。

Android 依赖：SDK `%LOCALAPPDATA%/Android/Sdk`，本机 JDK 为 `C:/Program Files/Android/Android Studio/jbr`。Godot Editor Settings 中须配置 Java/Android SDK。现有预设使用非 Gradle 模板、arm64-v8a+x86_64、API 24 起、OpenGL ES 3.0；Android 更新使用 INTERNET 和 REQUEST_INSTALL_PACKAGES。改为其他导出方式前核对兼容和体积影响。

`build_godot.py` 使用 `build/debug.jks`；找不到时只为 Android 目标创建新开发 key。不要删除已有 key，不提交私钥。环境变量名称为 `GODOT_ANDROID_KEYSTORE_DEBUG_PASSWORD`，不是 `..._PASS`。

构建脚本把 TEMP/TMP 指向工作区 `build/godot-tmp/`，并暂时移走测试用 `override.cfg`。这是为解决工具运行环境的临时目录写入问题；出现 `Couldn't save project.binary` 时检查 TEMP/权限，不忽略错误。PowerShell 直接调用 GUI EXE 可能提前返回；自动化用 Python `subprocess.run` 或 `Start-Process -Wait` 等待真实退出。

共享示例以 `../app/src/main/assets/` 为构建脚本同步源，构建会覆盖 `godot/assets/` 中同名文件。改示例必须同时更新同步源，不只改 Godot 副本；保留音乐和字体许可。

| 产物/日志 | 当前路径 |
| --- | --- |
| Windows | `../dist/godot-0.13.0/MusounD.exe` + `MusounD.pck`，必须同目录 |
| Android | `../dist/MusounD-0.14.0.apk` |
| 构建日志 | `../build/godot-export-<Windows或Android>-output.log` |
| 版本信息 | `../release.json`、`project.godot`、`export_presets.cfg`、`core/chart_loader.gd`；Android 发布构建及签名清单的生成步骤见本目录 README |

提升版本时检查以上所有入口，不只修改窗口标题；不自动提升谱面 FORMAT。

## 验证方法

纯文档修改无需重新构建；代码修改选择相关层次验证。改核心/格式/几何时先运行核心测试。以下用 Python 等待真正的 Godot 进程，不会启动可见窗口：

```powershell
@'
from pathlib import Path
import subprocess
root = Path.cwd()
(root / 'build').mkdir(exist_ok=True)
exe = Path.home() / 'Desktop/Godot_v4.7.1-stable_win64.exe/Godot_v4.7.1-stable_win64.exe'
p = subprocess.run([str(exe), '--headless', '--path', str(root / 'godot'),
    '--script', 'tests/core_tests.gd', '--log-file', str(root / 'build/godot-core.log')],
    capture_output=True, text=True, encoding='utf-8', errors='replace',
    creationflags=subprocess.CREATE_NO_WINDOW)
print(p.stdout)
print(p.stderr)
raise SystemExit(p.returncode)
'@ | python -
```

检查退出码、测试失败数与 `SCRIPT ERROR`。2026-11-01 的发布基线为 189 项核心检查，完整记录在 `../docs/godot-0.13.0-tests.json`。偶见工具环境的系统证书读取错误不能当作脚本报错，也不能用它掩盖真正失败。

界面/音频改动还需实际渲染运行，分别检查 16:9、4:3，导出后再验证 EXE/PCK。诊断参数：

```text
Godot运行参数：--path godot -- --verify-output=<报告绝对路径.json>
导出EXE参数：-- --verify-output=<报告绝对路径.json>
其他启动参数：--import=<谱面包绝对路径>；--autoplay=<chart.json绝对路径>
```

诊断会改变所用用户目录的设置并写入测试成绩，使用隔离目录；可在源码运行时用未跟踪的 `override.cfg` 覆盖 `config/custom_user_dir_name`。不要将该文件打进产物。渲染/声音检查不要用 headless 结果代替，检查报告和实际截图，而非只看退出码。历史报告是基线，不是本轮运行结果。

### Android 联调

完整仓库、对应开发签名与一次性模拟器准备好后：

```powershell
python tools/build_godot_tests.py
$env:INTERVAL_TEST_DEVICE = 'emulator-5554'
python tools/test_godot_android.py
```

前提：模拟器已运行，已安装可由夹具写入数据的同包名/同签名客户端；测试脚本不是全新模拟器的一键安装器。要证明 0.12.0 覆盖迁移，先安装原 0.12.0 APK，再执行夹具和升级流程。夹具构建还依赖 `dist/0.12.0/Desultory-Signals-AT-Flow.interval`、共享示例和 `build/debug.jks`；干净检出缺失时先准备对应发行样本，不能误报已通过。

测试会删除测试 profile、写入旧成绩/曲库、安装夹具、改变模拟器尺寸；仅在一次性模拟器运行，不对用户真机数据运行。它通过真实 `content://` 导入 711 音符 AT Flow，测试旧设置/成绩、双比例播放、多指、Hold、取消触摸与暂停续接，结果在 `build/godot-android-tests/`。

本机 AVD 名为 Interval，`-gpu host -feature GLESDynamicVersion` 已验证可提供 GLES3；旧的 GLES2 限制启动参数会导致 EGL 失败。不要覆盖已有 AVD 配置或把 `.tools/` 当成完整 `ANDROID_AVD_HOME`。读取截图等二进制必须用 `adb exec-out`，不要用 `adb shell cat` 产生损坏 PNG。

当前发布仅有 Windows 和 Android 模拟器验证；真机触控、温升、音频延迟和不同厂商文件选择器仍需设备实测，不把自动测试等同于真机体验。

## 交付与下一位 Agent

- 新分支默认 `codex/` 前缀；提交只包含任务相关文件，不修改无关的 `web/`、制谱器或用户谱面。
- 不提交 `.godot/`、测试 `override.cfg`、`build/`、`dist/`、本机配置、签名或密钥；应保留源码对应的 `.gd.uid`、资源导入设置、字体与许可。
- `.release-repos/game` 是本机游戏仓库发布副本，不是默认开发目录。按当前授权同步/发布时核对远端、tag、版本、APK 签名、Windows EXE/PCK、源码包、许可和 SHA-256；保留旧发行版本。
- 最终说明实现变化、验证结果与限制，提供可定位的源码/产物/报告路径。未执行的检查必须标注，不用历史报告替代本次证据。
