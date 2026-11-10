# 场景化视觉编辑

客户端的界面和游戏画面使用 Godot 原生节点。`main.gd` 负责状态、数据和按钮动作；`ui/playfield.gd` 负责谱面几何和节点属性更新。两者不再使用 `_draw()` 或 `draw_*()` 绘制界面，也不再在切换页面时创建和销毁 Button/Label。

## 在编辑器中修改

导入 `project.godot`，打开 `main.tscn`。页面是独立实例，可从文件系统双击对应 `.tscn` 编辑；F5 运行完整客户端。请保留脚本引用的节点名称和层级。

| 内容 | 文件 |
| --- | --- |
| 总入口、页面实例、导入和提示对话框 | `main.tscn` |
| 首页及圆环、柱形装饰 | `ui/pages/title.tscn` |
| 选歌卡片、分页、歌曲详情 | `ui/pages/songs.tscn` |
| 设置及说明页面 | `ui/pages/settings.tscn`、`help.tscn` |
| 倒数、演奏按钮、暂停、结算 | `ui/pages/count.tscn`、`play.tscn`、`pause.tscn`、`result.tscn` |
| 游戏背景、固定判定线、动态图层 | `ui/playfield.tscn` |
| 分数、连击、判定、进度条、段落提示 | `ui/components/hud.tscn` |
| 短暂提示 | `ui/components/toast.tscn` |
| Tap/Hold 音符头、高光、Hold 尾端 | `ui/components/note_head.tscn`、`hold_tail.tscn` |
| 动态图形模板及复用池 | `ui/components/geometry_layer.tscn`、`polygon.tscn`、`polyline.tscn`、`rectangle.tscn` |
| 字体、按钮各状态、主按钮、面板样式 | `ui/theme.tres` |
| 音符/区域/轨迹/触点颜色、音符宽度、线宽、反馈时长 | `ui/playfield_skin.tres` |

按钮普通样式使用 Theme 的 `Button`，强调按钮使用 `Primary` 类型变体。页面 Label 的局部字号和颜色可以直接在 Inspector 调整。共享字体使用 FontVariation，字重为 400。

页面使用水平锚点和偏移：逻辑高度 600，宽度随比例在 800–1066.6667 间变化；外层负责统一缩放和超宽居中。移动界面元素优先修改场景中的锚点/偏移。游戏判定线、边界和几何坐标同时参与输入转换，改变玩法坐标布局时还需同步 `playfield.gd` 的坐标映射。

## 动态画面

区域、轨迹、弯折 Hold、打击圆环和触点的顶点随歌曲时间变化，仍由脚本计算。渲染载体已经是 `.tscn` 实例中的 `Polygon2D`、`Line2D` 和 `Control/ColorRect`，分为 Regions、Tracks、Notes、Effects 图层。实例池复用节点，避免逐帧销毁重建。运行时可在 Remote 场景树中查看它们。

`playfield_skin.tres` 控制脚本会更新的动态外观。几何模板中的顶点、颜色和线宽会被运行时数据覆盖；音符头子节点的尺寸、高光和结构可以直接在场景里编辑。HUD 和所有菜单布局都由场景保存。

单独预览菜单场景可编辑静态布局；动态曲目画面需通过主场景选歌运行。

## 本次验证

- `tests/core_tests.gd`：189 项核心检查通过。
- `tests/scene_tests.gd`：场景按钮绑定、选歌、设置、返回、节点复用及两种逻辑宽度布局检查通过。
- 在隔离工程/用户目录中进行 16:9、4:3 实际渲染诊断，音乐、双指、Hold、触摸取消及暂停续接检查通过。
- 本次报告和截图位于源码根目录 `build/scene-ui-qa/`；测试导出采用独立的 QA 用户目录，不是正式发行包。
- Android 真机未在本次重新验证。
- Windows EXE/PCK 导出成功；从普通临时目录运行后，15 项打包运行诊断全部通过（`build/scene-ui-qa/exported.json`）。工作区目录仍带低完整性继承标记，直接在该目录运行新导出的 EXE 曾在启动阶段失败；复制测试未修改系统权限。
