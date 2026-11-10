extends Control
const Core=preload("res://core/rhythm_core.gd")
const Loader=preload("res://core/chart_loader.gd")
const Library=preload("res://services/library.gd")
const Profile=preload("res://services/profile.gd")
const Clock=preload("res://services/audio_clock.gd")
const Field=preload("res://ui/playfield.gd")
const VERSION="0.14.0"
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
@onready var ui=$UI
var w=1066.6667
var scale_factor=1.0
var origin=Vector2.ZERO
@onready var dialog=$ImportDialog
@onready var message_dialog=$MessageDialog
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
	clock=Clock.new(); add_child(clock)
	field=$Playfield
	if OS.get_name()!="Android": dialog.filters=PackedStringArray(["*.interval,*.zip ; INTERVAL 谱面包"])
	dialog.file_selected.connect(import_chart)
	bind_ui()
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
	ui.position=origin; ui.scale=Vector2.ONE*scale_factor; ui.size=Vector2(w,600)
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
	toast=message; toast_until=Time.get_ticks_msec()+6000

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
	state=next; build_ui()

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
	refresh_live_ui()

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

func adjust_setting(key: String,delta: float):
	var bounds={"approach":Vector2(0.6,4),"offset":Vector2(-300,300),"hit_volume":Vector2(0,2)}[key]
	profile.settings[key]=snappedf(clampf(profile.settings[key]+delta,bounds.x,bounds.y),0.1 if key!="offset" else 1)
	profile.save(); build_ui()

func toggle_setting(key: String):
	profile.settings[key]=not profile.settings[key]; profile.save(); build_ui()


# Scene bindings contain data/actions only; layout and styling live in .tscn/.tres.
func bind_button(path: String, action: Callable):
	ui.get_node(path).pressed.connect(action)

func bind_ui():
	bind_button("Title/Start",func():change_state(SONGS))
	bind_button("Title/Help",func():utility_return=TITLE;change_state(HELP))
	bind_button("Title/Settings",func():utility_return=TITLE;change_state(SETTINGS))
	bind_button("Songs/Home",func():change_state(TITLE))
	bind_button("Songs/Import",func():dialog.popup_centered_ratio(0.8))
	bind_button("Songs/Previous",change_page.bind(-1))
	bind_button("Songs/Next",change_page.bind(1))
	bind_button("Songs/Play",start_song.bind(false))
	bind_button("Songs/Auto",start_song.bind(true))
	bind_button("Songs/Settings",func():utility_return=SONGS;change_state(SETTINGS))
	for row in range(4): bind_button("Songs/Song"+str(row),func():select_song(page*4+row))
	for pair in [["Approach","approach",0.2],["Offset","offset",10.0],["Volume","hit_volume",0.1]]:
		bind_button("Settings/"+pair[0]+"Minus",adjust_setting.bind(pair[1],-pair[2]))
		bind_button("Settings/"+pair[0]+"Plus",adjust_setting.bind(pair[1],pair[2]))
	bind_button("Settings/Guide",toggle_setting.bind("guide"))
	bind_button("Settings/Sounds",toggle_setting.bind("hit_sounds"))
	for path in ["Settings/Back","Help/Back"]: bind_button(path,func():change_state(utility_return))
	for path in ["Count/Pause","Play/Pause"]: bind_button(path,pause)
	bind_button("Pause/Resume",resume)
	for path in ["Pause/Restart","Result/Restart"]: bind_button(path,func():launch(current_song,auto))
	for path in ["Pause/Back","Result/Back"]: bind_button(path,leave_game)

func set_text(path: String,value):
	ui.get_node(path).text=str(value)

func build_ui():
	if not is_node_ready(): return
	var available=get_viewport_rect().size
	scale_factor=minf(available.y/600.0,available.x/800.0); w=minf(1066.6667,available.x/scale_factor)
	origin=(available-Vector2(w,600)*scale_factor)/2.0
	ui.position=origin; ui.scale=Vector2.ONE*scale_factor; ui.size=Vector2(w,600)
	var pages=["Title","Songs","Settings","Help","Count","Play","Pause","Result"]
	for i in range(pages.size()): ui.get_node(pages[i]).visible=i==state
	field.visible=state in [PLAY,COUNT,PAUSE,RESULT]
	if state==SONGS: refresh_songs()
	if state==SETTINGS:
		set_text("Settings/ApproachValue","%.1f s" % profile.settings.approach)
		set_text("Settings/OffsetValue","%d ms" % profile.settings.offset)
		set_text("Settings/VolumeValue","%d%%" % roundi(profile.settings.hit_volume*100))
		set_text("Settings/Guide","开启" if profile.settings.guide else "关闭")
		set_text("Settings/Sounds","打击音：开" if profile.settings.hit_sounds else "打击音：关")
	if state==RESULT: refresh_results()
	refresh_live_ui()

func refresh_live_ui():
	if not is_node_ready(): return
	ui.get_node("Toast").visible=not toast.is_empty() and Time.get_ticks_msec()<toast_until
	set_text("Toast/Message",toast)
	if state==COUNT: set_text("Count/Number",maxi(1,ceili((count_end-Time.get_ticks_msec())/1000.0)))

func refresh_songs():
	set_text("Songs/Count","曲库 / %d 首谱面" % songs.size())
	set_text("Songs/Import","正在导入…" if importing else "＋ 导入谱面")
	ui.get_node("Songs/Import").disabled=importing or scanning
	ui.get_node("Songs/Scanning").visible=scanning
	ui.get_node("Songs/Previous").disabled=page<=0
	ui.get_node("Songs/Next").disabled=(page+1)*4>=songs.size()
	set_text("Songs/Page","%d / %d" % [page+1,maxi(1,ceili(songs.size()/4.0))])
	set_text("Songs/Status","%d 份谱面无法读取，已跳过" % unavailable if unavailable>0 else "支持 .interval · 歌曲与谱面一同导入")
	for row in range(4):
		var index=page*4+row; var card=ui.get_node("Songs/Song"+str(row))
		card.visible=index<songs.size()
		if not card.visible: continue
		card.set_pressed_no_signal(index==selected)
		var c=songs[index].chart
		card.get_node("Title").text=c.title
		card.get_node("Subtitle").text=c.artist+" / "+c.difficulty
		card.get_node("Meta").text=("内置" if songs[index].builtin else "导入")+" · "+Field.format_time(c.duration)+" · %.0f BPM" % c.bpm
	for name in ["Details","Source","Difficulty","SongTitle","Artist","Tempo","Notes","Format","Best","Play","Auto"]:
		ui.get_node("Songs/"+name).visible=not songs.is_empty()
	for name in ["Play","Auto"]: ui.get_node("Songs/"+name).disabled=importing
	if songs.is_empty(): return
	var song=songs[selected]; var c=song.chart
	set_text("Songs/Source","BUILT-IN / 内置" if song.builtin else "CUSTOM / 导入")
	set_text("Songs/Difficulty",c.difficulty); set_text("Songs/SongTitle",c.title); set_text("Songs/Artist",c.artist)
	set_text("Songs/Tempo","%.1f BPM / %s" % [c.bpm,Field.format_time(c.duration)])
	set_text("Songs/Notes","%d 音符 · %d HOLD" % [c.notes.size(),song.holds])
	set_text("Songs/Format","%d 段 · %s / v%d" % [c.sections.size(),c.app_version,c.version])
	set_text("Songs/Best","BEST  %07d" % profile.best(song))

func refresh_results():
	set_text("Result/Mode","AUTOPLAY / NOT RANKED" if auto else "SESSION COMPLETE")
	set_text("Result/Title","演示结束。" if auto else "这一次，节奏属于你。")
	set_text("Result/Song",chart.title+" / "+chart.artist)
	var rank="AP" if engine.miss==0 and engine.good==0 else ("FC" if engine.miss==0 else ("S" if engine.accuracy()>=95 else ("A" if engine.accuracy()>=85 else ("B" if engine.accuracy()>=70 else "C"))))
	set_text("Result/Rank",rank); set_text("Result/Score","%07d" % engine.score())
	set_text("Result/PerfectValue",engine.perfect); set_text("Result/GoodValue",engine.good); set_text("Result/MissValue",engine.miss)
	set_text("Result/Accuracy","ACCURACY %.2f%%   MAX COMBO %d" % [engine.accuracy(),engine.max_combo])
	set_text("Result/Timing","自动演示成绩不会保存为个人纪录。" if auto else "平均时差 %d ms" % roundi(engine.absolute_error/maxi(1,engine.timed_hits)*1000))
