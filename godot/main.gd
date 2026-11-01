extends Control
const Core=preload("res://core/rhythm_core.gd")
const Loader=preload("res://core/chart_loader.gd")
const Library=preload("res://services/library.gd")
const Profile=preload("res://services/profile.gd")
const Clock=preload("res://services/audio_clock.gd")
const Field=preload("res://ui/playfield.gd")
const VERSION="0.13.0"
const BG=Color("10191b")
const PANEL=Color("182527")
const INK=Color("edf5eb")
const MUTED=Color("8da5a4")
const MINT=Color("a6f4cf")
const ORANGE=Color("ffb682")
enum {TITLE,SONGS,SETTINGS,HELP,COUNT,PLAY,PAUSE,RESULT}
var state=TITLE
var utility_return=TITLE
var songs=[]
var unavailable=0
var selected=0
var page=0
var scanning=true
var importing=false
var job: Thread
var profile=Profile.new()
var clock
var field
var engine
var chart={}
var current_song={}
var auto=false
var frozen=0.0
var count_end=0
var end_audio_time=0.0
var ui=Control.new()
var font: Font
var w=1066.6667
var scale_factor=1.0
var origin=Vector2.ZERO
var dialog=FileDialog.new()
var message_dialog=AcceptDialog.new()
var toast=""
var toast_until=0
var swipe_start=Vector2.ZERO
var swipe_active=false
var pending_import=""

func _ready():
	mouse_filter=Control.MOUSE_FILTER_IGNORE
	get_tree().auto_accept_quit=false
	get_tree().quit_on_go_back=false
	DisplayServer.screen_set_keep_on(true)
	if ResourceLoader.exists("res://assets/fonts/NotoSansSC.ttf"):
		font=FontVariation.new(); font.base_font=load("res://assets/fonts/NotoSansSC.ttf"); font.variation_opentype={"wght":400.0}
	else:
		font=SystemFont.new(); font.font_names=PackedStringArray(["Microsoft YaHei UI","Noto Sans CJK SC","sans-serif"])
	var theme=Theme.new(); theme.default_font=font; theme.default_font_size=16; self.theme=theme
	clock=Clock.new(); add_child(clock)
	field=Field.new(); field.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); field.font=font; field.show_behind_parent=true; add_child(field)
	ui.mouse_filter=Control.MOUSE_FILTER_IGNORE; add_child(ui)
	dialog.file_mode=FileDialog.FILE_MODE_OPEN_FILE; dialog.access=FileDialog.ACCESS_FILESYSTEM; dialog.use_native_dialog=true
	# No extension filter on Android: .interval has no registered MIME type there.
	if OS.get_name()!="Android": dialog.filters=PackedStringArray(["*.interval,*.zip ; INTERVAL 谱面包"])
	dialog.file_selected.connect(import_chart); add_child(dialog)
	message_dialog.title="MusounD"; add_child(message_dialog)
	get_viewport().size_changed.connect(resize_ui)
	build_ui(); scan_library()
	if OS.is_debug_build() and FileAccess.file_exists("user://diagnostic-request.json"):
		var request=JSON.parse_string(FileAccess.get_file_as_string("user://diagnostic-request.json"))
		DirAccess.remove_absolute("user://diagnostic-request.json")
		if request is Dictionary and str(request.get("report","")).begins_with("user://"):
			var diagnostics=preload("res://services/diagnostics.gd").new(); add_child(diagnostics)
			diagnostics.call_deferred("run",self,request.report,request)
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--verify-output="):
			var diagnostics=preload("res://services/diagnostics.gd").new(); add_child(diagnostics); diagnostics.call_deferred("run",self,arg.trim_prefix("--verify-output="))
		if arg.begins_with("--import="): pending_import=arg.trim_prefix("--import=")
		if arg.begins_with("--autoplay="): call_deferred("launch_path",arg.trim_prefix("--autoplay="),true)

func _exit_tree():
	if job!=null and job.is_started(): job.wait_to_finish()
	if clock!=null: clock.stop()

func _notification(what):
	if what in [NOTIFICATION_APPLICATION_FOCUS_OUT,NOTIFICATION_APPLICATION_PAUSED]: pause()
	if what==NOTIFICATION_WM_GO_BACK_REQUEST: back()
	if what==NOTIFICATION_WM_CLOSE_REQUEST: profile.save(); get_tree().quit()

func resize_ui():
	var available=get_viewport_rect().size
	scale_factor=minf(available.y/600.0,available.x/800.0); w=minf(1066.6667,available.x/scale_factor)
	origin=(available-Vector2(w,600)*scale_factor)/2.0
	ui.position=origin; ui.scale=Vector2.ONE*scale_factor
	if field!=null: field.layout()
	build_ui()

func scan_library():
	if job!=null and job.is_started(): return
	scanning=true; build_ui()
	job=Thread.new(); job.start(func(): var result=Library.new().scan(); scan_done.call_deferred(result))

func scan_done(result: Dictionary):
	job.wait_to_finish(); job=null; scanning=false; songs=result.songs; unavailable=result.unavailable
	selected=0
	for i in range(songs.size()):
		if songs[i].id==profile.selected: selected=i; break
	page=selected/4; build_ui()
	if not pending_import.is_empty():
		var path=pending_import; pending_import=""; import_chart.call_deferred(path)

func notice(message: String):
	toast=message; toast_until=Time.get_ticks_msec()+6000; queue_redraw()

func error_message(message: String):
	message_dialog.dialog_text=message; message_dialog.popup_centered(Vector2i(600,220))

func import_chart(path: String):
	if importing or scanning: notice("请等待当前曲库操作完成"); return
	importing=true; build_ui(); notice("正在导入谱面包…")
	job=Thread.new()
	job.start(func():
		var library=Library.new(); var item=library.import_package(path)
		import_done.call_deferred(item,library.error))

func import_done(item: Dictionary,message: String):
	job.wait_to_finish(); job=null; importing=false
	if item.is_empty(): error_message("导入失败：\n"+message); build_ui(); return
	profile.selected=item.id; profile.save(); state=SONGS; notice("已加入本机曲库"); scan_library()

func select_song(index: int):
	selected=index; profile.selected=songs[index].id; profile.save(); build_ui()

func change_state(next: int):
	state=next; build_ui(); queue_redraw()

func start_song(demo: bool):
	if songs.is_empty() or importing: return
	launch(songs[selected],demo)

func launch_path(path: String,demo: bool):
	var item=Library.new().song(path)
	if item.is_empty(): error_message("无法打开指定谱面"); return
	launch(item,demo)

func launch(song: Dictionary,demo: bool):
	clock.stop()
	var loader=Loader.new(); chart=loader.load_path(song.path)
	if chart.is_empty(): error_message(loader.error); return
	var library=Library.new(); var stream=library.audio_stream(song.audio)
	if stream==null: error_message("歌曲解码失败，请用制谱器重新导出 Ogg。\n"+library.error); return
	current_song=song; auto=demo; engine=Core.new(chart); engine.listener=judged
	clock.prepare(stream); clock.enabled=profile.settings.hit_sounds; clock.volume=profile.settings.hit_volume
	field.chart=chart; field.engine=engine; field.effects=[]; field.feedback_until=0; field.autoplay=auto
	field.approach=profile.settings.approach; field.guide=profile.settings.guide; field.visible=true; field.layout()
	frozen=-chart.offset-profile.settings.offset/1000.0; engine.time=frozen
	end_audio_time=maxf(chart.duration,stream.get_length())
	for n in chart.notes: end_audio_time=maxf(end_audio_time,n.time+n.duration+chart.offset+profile.settings.offset/1000.0+Core.MISS+0.01)
	begin_countdown()

func begin_countdown():
	count_end=Time.get_ticks_msec()+3000; state=COUNT; build_ui()

func music_time() -> float:
	return clock.position()-chart.offset-profile.settings.offset/1000.0

func judged(note: Dictionary,grade: String,delta: float):
	field.judged(note,grade,delta)
	if grade=="HOLD" or (note.duration==0 and grade!="MISS"): clock.hit(note.duration>0)

func pause():
	if not state in [PLAY,COUNT]: return
	if state==PLAY: frozen=music_time()
	clock.pause(); engine.suspend(); engine.time=frozen; state=PAUSE; build_ui()

func resume():
	if state==PAUSE: begin_countdown()

func leave_game():
	clock.stop()
	if engine!=null: engine.suspend()
	field.visible=false; state=SONGS; build_ui()

func back():
	match state:
		PLAY,COUNT: pause()
		PAUSE,RESULT: leave_game()
		SONGS: change_state(TITLE)
		SETTINGS,HELP: change_state(utility_return)
		TITLE: get_tree().quit()

func complete():
	clock.stop(); engine.finish_all(); frozen=engine.time; state=RESULT
	if not auto: profile.record(current_song,engine.score())
	build_ui()

func _process(_delta):
	if state==COUNT and Time.get_ticks_msec()>=count_end:
		clock.play_from(maxf(0.0,frozen+chart.offset+profile.settings.offset/1000.0)); state=PLAY; build_ui()
	if state==PLAY:
		var time=music_time()
		if auto: engine.autoplay(time)
		else: engine.update(time)
		field.time=time
		if clock.position()>=end_audio_time: complete()
	elif state in [COUNT,PAUSE,RESULT]: field.time=frozen
	if state in [PLAY,COUNT,PAUSE,RESULT]: field.queue_redraw()
	queue_redraw()

func _input(event):
	if state!=SONGS: return
	if event is InputEventScreenTouch or (event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT):
		var position=(event.position-origin)/scale_factor
		if event.pressed: swipe_start=position; swipe_active=true
		elif swipe_active:
			var delta=position-swipe_start; swipe_active=false
			if absf(delta.x)>60 and absf(delta.y)<60:
				change_page(1 if delta.x<0 else -1); get_viewport().set_input_as_handled()

func _unhandled_input(event):
	if event is InputEventKey and event.pressed and not event.echo:
		if event.physical_keycode in [KEY_ESCAPE,KEY_BACK]: back(); get_viewport().set_input_as_handled(); return
		if event.physical_keycode==KEY_SPACE and state in [PLAY,COUNT,PAUSE]:
			if state==PAUSE: resume()
			else: pause()
			get_viewport().set_input_as_handled(); return
	if event is InputEventKey and not event.echo and state in [PLAY,COUNT] and not auto:
		var i=[KEY_D,KEY_F,KEY_J,KEY_K].find(event.physical_keycode)
		if i>=0:
			var id=-i-2; var x=[0.18,0.39,0.61,0.82][i]; var t=music_time() if state==PLAY else frozen
			if event.pressed:
				var y=engine.touch_y(x,t)
				if y!=null: pointer_down(id,Vector2(x,float(y)))
			elif state==PLAY: engine.up(id,t)
			else: release_count_pointer(id)
			get_viewport().set_input_as_handled(); return
	if state in [PLAY,COUNT] and not auto:
		if event is InputEventScreenTouch:
			var p=field.point(event.position)
			if event.canceled:
				if state==PLAY: engine.cancel(music_time())
				else: engine.suspend()
			elif event.pressed: pointer_down(event.index,p)
			elif state==PLAY: engine.up(event.index,music_time())
			else: release_count_pointer(event.index)
		elif event is InputEventScreenDrag:
			pointer_move(event.index,field.point(event.position))
		elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT:
			if event.pressed: pointer_down(100,field.point(event.position))
			elif state==PLAY: engine.up(100,music_time())
			else: release_count_pointer(100)
		elif event is InputEventMouseMotion and event.button_mask&MOUSE_BUTTON_MASK_LEFT: pointer_move(100,field.point(event.position))

func pointer_down(id: int,p: Vector2):
	if state==PLAY: engine.down(id,p.x,p.y,music_time())
	elif state==COUNT: engine.reattach(id,p.x,p.y)

func pointer_move(id: int,p: Vector2):
	if not engine.pointers.has(id): return
	if state==PLAY: engine.move(id,p.x,p.y,music_time())
	elif state==COUNT: engine.pointers[id]=p

func release_count_pointer(id: int):
	engine.pointers.erase(id)
	for n in engine.notes:
		if n.pointer==id: n.pointer=-1

func change_page(delta: int):
	page=clampi(page+delta,0,maxi(0,(songs.size()-1)/4)); build_ui()

func button(label: String,rect: Rect2,action: Callable,primary: bool=false,enabled: bool=true) -> Button:
	var b=Button.new(); b.text=label; b.position=rect.position; b.size=rect.size; b.disabled=not enabled
	for state_name in ["normal","hover","pressed","disabled","focus"]:
		var style=StyleBoxFlat.new(); style.bg_color=MINT if primary else PANEL
		if state_name=="hover": style.bg_color=Color("c4f9df") if primary else Color("29423b")
		if state_name=="disabled": style.bg_color=Color("304244")
		style.set_corner_radius_all(7); style.set_border_width_all(1); style.border_color=Color("304244")
		if state_name=="focus": style.bg_color=Color(0,0,0,0); style.border_color=MINT; style.set_border_width_all(2)
		b.add_theme_stylebox_override(state_name,style)
	for name in ["font_color","font_hover_color","font_pressed_color","font_focus_color"]: b.add_theme_color_override(name,BG if primary else INK)
	b.pressed.connect(action); ui.add_child(b); return b

func build_ui():
	if not is_inside_tree(): return
	for child in ui.get_children(): ui.remove_child(child); child.queue_free()
	var available=get_viewport_rect().size; scale_factor=minf(available.y/600.0,available.x/800.0); w=minf(1066.6667,available.x/scale_factor)
	origin=(available-Vector2(w,600)*scale_factor)/2.0; ui.position=origin; ui.scale=Vector2.ONE*scale_factor
	if field!=null: field.visible=state in [PLAY,COUNT,PAUSE,RESULT]
	match state:
		TITLE:
			button("进入选歌   →",Rect2(48,389,302,58),func():change_state(SONGS),true)
			button("玩法说明",Rect2(48,462,145,42),func():utility_return=TITLE;change_state(HELP))
			button("设置",Rect2(205,462,145,42),func():utility_return=TITLE;change_state(SETTINGS))
		SONGS:
			button("← 首页",Rect2(48,24,102,40),func():change_state(TITLE))
			button("正在导入…" if importing else "＋ 导入谱面",Rect2(w-210,24,162,40),func():dialog.popup_centered_ratio(0.8),true,not importing and not scanning)
			var lw=(w-120)*0.48; var dx=48+lw+24; var dw=w-dx-48
			for row in range(4):
				var i=page*4+row
				if i>=songs.size(): break
				var b=button("",Rect2(48,140+row*86,lw,76),select_song.bind(i))
				b.modulate=Color("c9f4dd") if i==selected else Color.WHITE
				var c=songs[i].chart
				var values=[c.title,c.artist+" / "+c.difficulty,("内置" if songs[i].builtin else "导入")+" · "+Field.format_time(c.duration)+" · %.0f BPM" % c.bpm]
				for j in range(3):
					var label=Label.new(); label.position=Vector2(16,[7,31,51][j]); label.size=Vector2(lw-32,24 if j==0 else 18)
					label.text=values[j]; label.add_theme_font_size_override("font_size",[17,12,10][j]); label.add_theme_color_override("font_color",INK if j==0 else MUTED)
					label.text_overrun_behavior=TextServer.OVERRUN_TRIM_ELLIPSIS; label.mouse_filter=Control.MOUSE_FILTER_IGNORE; b.add_child(label)
			button("←",Rect2(48,498,62,39),change_page.bind(-1),false,page>0)
			button("→",Rect2(48+lw-62,498,62,39),change_page.bind(1),false,(page+1)*4<songs.size())
			if not songs.is_empty():
				button("开始演奏  →",Rect2(dx,462,dw,48),start_song.bind(false),true,not importing)
				button("自动演示",Rect2(dx,521,(dw-12)/2,37),start_song.bind(true),false,not importing)
				button("设置",Rect2(dx+(dw+12)/2,521,(dw-12)/2,37),func():utility_return=SONGS;change_state(SETTINGS))
		SETTINGS:
			button("−",Rect2(w-330,185,55,48),adjust_setting.bind("approach",-0.2))
			button("＋",Rect2(w-103,185,55,48),adjust_setting.bind("approach",0.2))
			button("−",Rect2(w-330,289,55,48),adjust_setting.bind("offset",-10))
			button("＋",Rect2(w-103,289,55,48),adjust_setting.bind("offset",10))
			button("开启" if profile.settings.guide else "关闭",Rect2(w-204,392,156,42),toggle_setting.bind("guide"))
			button("打击音：开" if profile.settings.hit_sounds else "打击音：关",Rect2(w-204,451,156,38),toggle_setting.bind("hit_sounds"))
			button("−",Rect2(w-330,501,55,38),adjust_setting.bind("hit_volume",-0.1))
			button("＋",Rect2(w-103,501,55,38),adjust_setting.bind("hit_volume",0.1))
			button("返回",Rect2(48,527,130,42),func():change_state(utility_return))
		HELP: button("返回",Rect2(48,522,150,43),func():change_state(utility_return))
		PLAY,COUNT: button("Ⅱ",Rect2(w-95,20,47,45),pause)
		PAUSE:
			button("继续演奏  →",Rect2(w/2-194,275,388,49),resume,true)
			button("重新开始",Rect2(w/2-194,338,188,43),func():launch(current_song,auto))
			button("返回选歌",Rect2(w/2+6,338,188,43),leave_game)
		RESULT:
			button("返回选歌",Rect2(48,522,156,43),leave_game)
			button("再来一次 →",Rect2(w-248,522,200,43),func():launch(current_song,auto),true)
	queue_redraw()

func adjust_setting(key: String,delta: float):
	var bounds={"approach":Vector2(0.6,4),"offset":Vector2(-300,300),"hit_volume":Vector2(0,2)}[key]
	profile.settings[key]=snappedf(clampf(profile.settings[key]+delta,bounds.x,bounds.y),0.1 if key!="offset" else 1)
	profile.save(); build_ui()

func toggle_setting(key: String):
	profile.settings[key]=not profile.settings[key]; profile.save(); build_ui()

func text(value: String,x: float,y: float,size: int=14,color: Color=INK,align: int=0,max_width: float=-1):
	if max_width>0:
		while value.length()>1 and font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x>max_width: value=value.left(value.length()-2)+"…"
	var length=font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
	draw_string(font,Vector2(x-length*align/2.0,y),value,HORIZONTAL_ALIGNMENT_LEFT,-1,size,color)

func _draw():
	if font==null: return
	if not state in [PLAY,COUNT,PAUSE,RESULT]: draw_rect(get_viewport_rect(),BG)
	draw_set_transform(origin,0,Vector2.ONE*scale_factor)
	match state:
		TITLE:
			text("M / MUSOUND",48,49,18,MINT); text("OFFLINE RHYTHM",w-48,47,11,MUTED,2)
			draw_line(Vector2(48,72),Vector2(w-48,72),Color("304244"))
			text("FIND YOUR",50,156,15,MINT); text("INTERVAL.",45,230,63 if w<900 else 76)
			text("区 间",51,279,27); text("让每一次触碰，都落在节奏里。",51,319,18,MUTED)
			var center=Vector2(w-193,242)
			for i in range(4): draw_arc(center,80+i*18,0,TAU,90,Color("304244"),1,true)
			for i in range(4):
				var length=[58,120,86,151][i]; var x=center.x-91+i*48
				draw_rect(Rect2(x,center.y+70-length,29,length),Color("294b43") if i%2==0 else Color("4c4032"))
				draw_rect(Rect2(x-3,center.y+70-length,35,7),MINT if i%2==0 else ORANGE)
			draw_line(center+Vector2(-117,83),center+Vector2(117,83),MINT,2)
			text("TAP / HOLD · 二维判定区域",48,558,12,MUTED); text("16:9 + 4:3 / Godot "+VERSION,w-48,558,11,MUTED,2)
		SONGS: draw_library()
		SETTINGS:
			text("TUNE YOUR SESSION",48,49,18,MINT); text("找到舒服的手感。",48,136,34)
			text("下落时间",48,207,20); text("数字越小，下落越快；不会改变歌曲速度。",48,238,14,MUTED)
			text("%.1f s" % profile.settings.approach,w-202,218,22,MINT,1)
			text("音符延迟补偿",48,309,20); text("正值使音符更晚到线，设置会自动保存。",48,340,14,MUTED)
			text(str(int(profile.settings.offset))+" ms",w-202,322,22,MINT,1)
			text("区域辅助显示",48,412,20); text("显示开放区域填色，边界始终保留。",48,439,13,MUTED)
			text("打击音效",48,479,20); text("音量",w-370,526,13,MUTED)
			text("%d%%" % roundi(profile.settings.hit_volume*100),w-202,526,19,MINT,1)
		HELP:
			text("HOW TO PLAY",48,49,18,MINT); text("在开放的区间内，跟随节奏。",48,135,30)
			var lines=["Tap：在音符到达下方固定判定线时点击。","Hold：持续按住，跟随它与下方判定线的交点。","点击高度不限，但必须在当前开放区域内，并横向对准音符。","区域可平移、旋转、伸缩；重叠区域按并集判定。","轨迹与 Hold 的弯折形状竖直下落，Tap 下落时横坐标固定。","支持多点触控；电脑可用鼠标或 D / F / J / K 演奏。","导入 .interval 后歌曲复制到本机；自动演示不记录成绩。","切后台或失去焦点自动暂停，继续前倒数 3 秒。"]
			for i in range(lines.size()): text(lines[i],48,190+i*38,16,MUTED,0,w-96)
		COUNT:
			draw_style_box(panel_style(),Rect2(w/2-155,140,310,155))
			text(str(maxi(1,ceili((count_end-Time.get_ticks_msec())/1000.0))),w/2,226,58,MINT,1)
			text("准备进入区间 · 可重新按住 Hold",w/2,269,13,INK,1)
		PAUSE:
			draw_rect(Rect2(0,88,w,512),Color(0.06,0.1,0.11,0.87)); draw_style_box(panel_style(),Rect2(w/2-238,158,476,290))
			text("节奏暂歇",w/2,212,33,INK,1); text("音乐与谱面已同步暂停",w/2,246,14,MUTED,1)
			text("继续前有 3 秒倒数，可重新按住 Hold",w/2,416,12,MUTED,1)
		RESULT: draw_results()
	if not toast.is_empty() and Time.get_ticks_msec()<toast_until:
		draw_style_box(panel_style(),Rect2(w/2-300,550,600,40)); text(toast,w/2,576,14,MINT,1,570)
	draw_set_transform(Vector2.ZERO)

func panel_style() -> StyleBoxFlat:
	var box=StyleBoxFlat.new(); box.bg_color=PANEL; box.set_corner_radius_all(8); return box

func draw_library():
	text("选择歌曲",169,51,22); draw_line(Vector2(48,82),Vector2(w-48,82),Color("304244"))
	var lw=(w-120)*0.48; var dx=48+lw+24; var dw=w-dx-48
	text("曲库 / %d 首谱面" % songs.size(),48,118,14,MUTED); text("TRACK DETAILS",dx,118,12,MINT)
	if scanning: text("正在读取曲库…",64,187,17)
	text("%d / %d" % [page+1,maxi(1,ceili(songs.size()/4.0))],48+lw/2,523,13,MUTED,1)
	if not songs.is_empty():
		var song=songs[selected]; var c=song.chart
		draw_style_box(panel_style(),Rect2(dx,140,dw,309))
		text("BUILT-IN / 内置" if song.builtin else "CUSTOM / 导入",dx+20,178,11,MINT)
		text(c.difficulty,dx+20,211,21,INK,0,dw-40); text(c.title,dx+20,251,24,INK,0,dw-40)
		text(c.artist,dx+20,278,14,MUTED,0,dw-40)
		text("%.1f BPM / %s" % [c.bpm,Field.format_time(c.duration)],dx+20,310,14,MINT)
		text("%d 音符 · %d HOLD" % [c.notes.size(),song.holds],dx+20,337,13)
		text("%d 段 · %s / v%d" % [c.sections.size(),c.app_version,c.version],dx+20,363,13,MUTED)
		text("BEST  %07d" % profile.best(song),dx+20,419,24,MINT)
	text("%d 份谱面无法读取，已跳过" % unavailable if unavailable>0 else "支持 .interval · 歌曲与谱面一同导入",48,571,11,ORANGE if unavailable>0 else MUTED,0,lw)

func draw_results():
	draw_rect(Rect2(0,0,w,600),BG)
	text("AUTOPLAY / NOT RANKED" if auto else "SESSION COMPLETE",48,49,18,MINT)
	text("演示结束。" if auto else "这一次，节奏属于你。",48,136,32)
	text(chart.title+" / "+chart.artist,48,169,12,MUTED,0,w-96)
	var rank="AP" if engine.miss==0 and engine.good==0 else ("FC" if engine.miss==0 else ("S" if engine.accuracy()>=95 else ("A" if engine.accuracy()>=85 else ("B" if engine.accuracy()>=70 else "C"))))
	text(rank,48,303,100,MINT); text("%07d" % engine.score(),230,280,54); text("SCORE / 1,000,000",233,307,12,MUTED)
	var cw=(w-120)/3.0
	for i in range(3):
		var x=48+i*(cw+12); draw_style_box(panel_style(),Rect2(x,343,cw,86))
		text(["PERFECT","GOOD","MISS"][i],x+18,370,11,ORANGE if i==2 else MUTED)
		text(str([engine.perfect,engine.good,engine.miss][i]),x+18,407,30)
	text("ACCURACY %.2f%%   MAX COMBO %d" % [engine.accuracy(),engine.max_combo],48,467,14,MINT)
	text("自动演示成绩不会保存为个人纪录。" if auto else "平均时差 %d ms" % roundi(engine.absolute_error/maxi(1,engine.timed_hits)*1000),48,493,13,MUTED)
