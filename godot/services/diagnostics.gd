extends Node
## Packaged diagnostic: actual scene tree, audio, input, pause, restart and screenshots.
var host
var output=""
var checks=[]
var failures=[]

func run(client,report: String,request: Dictionary={}):
	host=client; output=report
	await get_tree().process_frame
	while host.scanning: await get_tree().process_frame
	check(host.songs.size()>=2,"built-in library")
	if request.has("legacy_score"):
		check(is_equal_approx(host.profile.settings.approach,1.4) and host.profile.settings.offset==20,"0.12 settings migration")
		check(host.profile.best(host.songs[0])==int(request.legacy_score),"0.12 SharedPreferences score migration")
		check(host.songs.any(func(s):return s.chart.title=="Legacy 0.12 migration"),"0.12 imported library survives update")
		for song in host.songs:
			if song.chart.title=="Legacy 0.12 migration": check(host.profile.best(song)==444444,"0.12 imported chart score migration")
	if request.has("import"):
		var count=host.songs.size(); host.import_chart(request["import"])
		while host.importing or host.scanning: await get_tree().process_frame
		check(host.songs.size()==count+1,"native content URI package import")
		check(host.songs.any(func(s):return not s.builtin and s.chart.notes.size()==711),"imported AT Flow retains 711 notes")
	await capture("title")
	host.change_state(host.SONGS); await get_tree().process_frame; await capture("songs")
	host.utility_return=host.SONGS; host.change_state(host.SETTINGS)
	host.adjust_setting("approach",-20); check(is_equal_approx(host.profile.settings.approach,0.6),"minimum 0.6s")
	host.adjust_setting("approach",1.6); host.toggle_setting("hit_sounds"); host.toggle_setting("hit_sounds")
	check(host.profile.save()==OK,"settings persist")
	await capture("settings")
	host.change_state(host.SONGS); host.select_song(0); host.start_song(true)
	check(host.state==host.COUNT,"start countdown")
	await get_tree().create_timer(4.8).timeout
	check(host.state==host.PLAY and host.clock.player.playing,"real music playback")
	check(host.music_time()>1.4 and host.music_time()<2.3,"audio-clock elapsed time")
	var first_end=host.chart.notes[0].time+host.chart.notes[0].duration+host.chart.offset+0.3
	while host.music_time()<first_end: await get_tree().process_frame
	check(host.engine.perfect>0 and host.engine.miss==0,"live autoplay judgement")
	await capture("game")
	host.pause(); var at=host.frozen; var music_at=host.clock.position()
	await get_tree().create_timer(0.25).timeout
	check(host.state==host.PAUSE and host.frozen==at and host.clock.position()==music_at,"pause freezes music and chart")
	await capture("pause")
	host.resume(); await get_tree().create_timer(3.3).timeout
	check(host.state==host.PLAY and host.music_time()>=at,"resume countdown and clock")
	check(host.engine.miss==0,"autoplay Hold reattaches")
	host.pause(); host.leave_game(); check(host.state==host.SONGS,"return to library")
	host.start_song(false); host.count_end=Time.get_ticks_msec()
	await get_tree().process_frame; await get_tree().process_frame
	check(host.engine.finished==0 and host.engine.pointers.is_empty(),"restart clears judgement")
	# Exercise the production touch event handlers against a controlled two-finger chord.
	var n0={"id":0,"time":host.music_time(),"duration":0.0,"line":host.engine.lines[0],"state":0,"pointer":-1,"outside_since":null,"grade":""}
	var n1=n0.duplicate(); n1.id=1; n1.line=host.engine.lines[3]
	host.engine.notes=[n0,n1]; host.field.layout()
	for i in range(2):
		var n=[n0,n1][i]; var event=InputEventScreenTouch.new(); event.index=i; event.pressed=true
		event.position=host.get_viewport().get_stretch_transform()*(host.field.origin+Vector2(host.field.nx(n.line.x(n.time)),420)*host.field.scale_factor)
		Input.parse_input_event(event)
	await get_tree().process_frame
	print("TOUCH_DIAGNOSTIC ",host.state," ",host.engine.perfect," ",host.engine.pointers," ",host.engine.time)
	check(host.engine.perfect==2,"two-finger touch input")
	var hold_note={"id":2,"time":host.music_time(),"duration":0.08,"line":host.engine.lines[0],"state":0,"pointer":-1,"outside_since":null,"grade":""}
	host.engine.notes=[hold_note]; host.engine.reset()
	var touch=InputEventScreenTouch.new(); touch.index=3; touch.pressed=true
	touch.position=host.get_viewport().get_stretch_transform()*(host.field.origin+Vector2(host.field.nx(hold_note.line.x(hold_note.time)),420)*host.field.scale_factor)
	Input.parse_input_event(touch)
	await get_tree().process_frame
	check(hold_note.state==1,"real touch Hold head attaches")
	var cancellation=InputEventScreenTouch.new(); cancellation.index=3; cancellation.pressed=false; cancellation.canceled=true; cancellation.position=touch.position
	Input.parse_input_event(cancellation)
	await get_tree().process_frame
	check(host.engine.miss==1,"touch cancellation cannot award a tail hit")
	host.pause(); host.engine.finish_all(); host.state=host.RESULT; host.build_ui(); await capture("results")
	var report_data={"ok":failures.is_empty(),"version":host.VERSION,"checks":checks,"failures":failures,"engine":Engine.get_version_info().string,"screen":str(get_viewport().get_visible_rect().size)}
	var f=FileAccess.open(output,FileAccess.WRITE)
	if f!=null: f.store_string(JSON.stringify(report_data,"  ")); f.close()
	else: printerr("Diagnostic report cannot be written: "+output)
	print("GODOT_RUNTIME_DIAGNOSTIC "+JSON.stringify(report_data))
	host.profile.save(); get_tree().quit(0 if failures.is_empty() else 1)

func check(value: bool,name: String):
	checks.append(name)
	if not value: failures.append(name); printerr("FAIL: "+name)

func capture(label: String):
	await get_tree().process_frame
	if DisplayServer.get_name()=="headless": return
	await RenderingServer.frame_post_draw
	var image=get_viewport().get_texture().get_image()
	image.save_png(output.get_basename()+"-"+label+".png")
