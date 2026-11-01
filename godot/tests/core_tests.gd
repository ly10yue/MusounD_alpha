extends SceneTree
const Core=preload("res://core/rhythm_core.gd")
const Loader=preload("res://core/chart_loader.gd")
var checks=0
var failures=[]

func check(value: bool,message: String):
	checks+=1
	if not value: failures.append(message); printerr("FAIL: "+message)

func _initialize():
	call_deferred("run")

func run():
	var loader=Loader.new()
	for name in ["demo","at-flow"]:
		var chart=loader.load_path("res://assets/"+name+".json")
		check(not chart.is_empty(),name+" parses: "+loader.error)
		if chart.is_empty(): continue
		for aspect in [(1066.6666667-96)/600.0,(800.0-96)/600.0]:
			for fps in [30,60,120]:
				var engine=Core.new(chart); engine.field_aspect=aspect
				var time=0.0
				while time<chart.duration+0.5:
					engine.autoplay(time); time+=1.0/fps
				check(engine.perfect==chart.notes.size() and engine.miss==0,"%s all perfect at %d fps / %f" % [name,fps,aspect])
				check(engine.score()==1000000,"complete score")
	behaviour_tests()
	print("Godot core: %d checks, %d failures" % [checks,failures.size()])
	quit(0 if failures.is_empty() else 1)

func minimal() -> Dictionary:
	return {"version":6,"appVersion":"0.12.0","minReaderVersion":"0.12.0","coordinateScale":100,"title":"Test","artist":"QA","difficulty":"TEST","bpm":120,"offset":0,"duration":6,"audio":"music.ogg","lines":[{"id":"a","frames":[{"time":0,"x":20,"alpha":1,"ease":"linear"}]},{"id":"b","frames":[{"time":0,"x":80,"alpha":0,"ease":"linear"}]}],"notes":[{"time":1,"type":"tap","line":"a"},{"time":2,"type":"hold","line":"a","duration":1}],"sections":[{"start":0,"end":6,"rects":[[0,50,45,100],[55,50,100,100]]}]}

func engine(): return Core.new(Loader.new().parse(minimal()))

func behaviour_tests():
	var e=engine()
	check(e.down(1,0.2,0.1,1)==null and e.notes[0].state==Core.WAIT,"outside region cannot consume note")
	e.down(2,0.2,0.9,1); check(e.perfect==1,"any allowed height judges")
	e=engine(); e.down(1,0.8,0.8,1); check(e.finished==0,"horizontal alignment required")
	e.down(2,0.2,0.8,1.1); check(e.good==1,"Good timing window")
	e=engine(); e.down(1,0.2,0.8,1.14); check(e.miss==1,"bad hit becomes Miss")
	e=engine(); e.down(1,0.2,0.8,0.8); check(e.finished==0,"too-early touch leaves note")
	e.update(1.161); check(e.miss==1,"timeout Miss")
	e=engine(); e.down(1,0.2,0.8,2); check(e.notes[1].state==Core.HELD,"Hold head attaches")
	e.up(1,2.5); check(e.notes[1].state==Core.LOST,"early release loses Hold")
	e=engine(); e.down(1,0.2,0.8,2); e.up(1,2.94); check(e.perfect==1,"80ms tail release tolerance")
	e=engine(); e.down(1,0.2,0.8,2); e.move(1,0.2,0.1,2.2); e.update(2.30); check(e.notes[1].state==Core.LOST,"region exit breaks Hold")
	e=engine(); e.down(1,0.2,0.8,2); e.move(1,0.2,0.1,2.2); e.move(1,0.2,0.8,2.24); e.update(3); check(e.perfect==1,"brief touch jitter tolerated")
	e=engine(); e.down(1,0.2,0.8,2); e.update(2.3); e.suspend(); e.reattach(2,0.2,0.8); e.update(3); check(e.perfect==1,"countdown Hold reattachment")
	e=engine(); e.down(1,0.2,0.8,2); e.suspend(); e.update(2.4); e.update(2.5); check(e.notes[1].state==Core.LOST,"unattached Hold loses after grace")
	e=engine(); e.down(1,0.2,0.8,2); e.cancel(2.2); check(e.notes[1].state==Core.LOST,"pointer cancellation")
	e.finish_all(); check(e.finished==2,"completion finalizes pending notes"); e.reset(); check(e.finished==0 and e.pointers.is_empty(),"reset clears judgement")
	var raw=minimal(); raw.notes=[{"type":"tap","time":1,"line":"a"},{"type":"tap","time":1,"line":"b"}]
	e=Core.new(Loader.new().parse(raw)); e.down(1,0.2,0.8,1); e.down(2,0.8,0.6,1); check(e.perfect==2 and e.max_combo==2,"simultaneous multi-touch / invisible track")
	var loader=Loader.new()
	for version in [1,2,3,4,5]:
		raw=minimal(); raw.version=version; raw.erase("coordinateScale"); raw.sections[0].rects=[[0,0,1,1]]
		if version<3:
			raw.erase("lines")
			for n in raw.notes: n.erase("line"); n.x=0.2
		else:
			for line in raw.lines: line.frames[0].x/=100.0
		var chart=loader.parse(raw); check(not chart.is_empty(),"legacy v%d parses: %s" % [version,loader.error])
		e=Core.new(chart)
		for i in range(241): e.autoplay(i/60.0)
		check(e.perfect==2,"legacy v%d still playable" % version)
	for mutation in [{"version":7},{"coordinateScale":1},{"minReaderVersion":"0.14.0"},{"minReaderVersion":"invalid"}]:
		raw=minimal(); raw.merge(mutation,true); check(loader.parse(raw).is_empty(),"invalid or future version rejected")
	raw=minimal(); raw.notes[0].line="missing"; check(loader.parse(raw).is_empty(),"unknown line rejected")
	raw=minimal(); raw.lines[0].events={"speed":[{"start":0,"end":2,"from":1,"to":9,"ease":"linear"}]}; check(loader.parse(raw).is_empty(),"invalid speed rejected")
	raw=minimal(); raw.lines[0].events={"x":[{"start":0,"end":2,"from":20,"to":80,"ease":"linear"},{"start":1,"end":3,"from":80,"to":20,"ease":"linear"}]}; check(loader.parse(raw).is_empty(),"overlapping parameter intervals rejected")
	raw=minimal(); raw.audio="../outside.ogg"; check(loader.parse(raw).is_empty(),"unsafe audio path rejected")
	var modern=loader.load_path("res://tests/fixtures/regions-v6.json"); var old=loader.load_path("res://tests/fixtures/regions-v5.json")
	check(not modern.is_empty() and not old.is_empty(),"actual Composer 0.11 snapshot migrates")
	var expected=JSON.parse_string(FileAccess.get_file_as_string("res://tests/fixtures/region-parity.json"))
	for sample in expected:
		var a=modern.sections[0].zones[0].polygon(sample.time,sample.aspect); var b=old.sections[0].zones[0].polygon(sample.time,sample.aspect)
		for i in range(4):
			check(a[i].distance_to(Vector2(sample.points[i][0],sample.points[i][1]))<0.000002,"Godot/Python region corner parity")
			check(a[i].distance_to(b[i])<0.000002,"old/new region geometry identical")
	for ease in Loader.EASES:
		var curve=Core.EventCurve.new(1.0,[{"start":1.0,"end":3.0,"from":1.0,"to":2.0,"ease":ease}]); var sum=0.0
		for i in range(4000): sum+=curve.value((i+0.5)/1000.0)/1000.0
		check(absf(sum-curve.distance(0,4))<0.002,"exact speed integral "+ease)
		check(absf(curve.distance(1,curve.horizon(1,1.0,6))-1.0)<0.00001,"speed horizon "+ease)
	var outline=load("res://core/region_outline.gd")
	var z=Core.Zone.new([0.1,0.2,0.6,0.7]); var z2=Core.Zone.new([0.4,0.2,0.9,0.7]); var edges=outline.edges([z.polygon(0,1.5),z2.polygon(0,1.5)])
	var inner=false
	for edge in edges:
		if absf(edge[0].x-0.4)<0.000001 and absf(edge[1].x-0.4)<0.000001: inner=true
	check(not inner,"overlap internal boundary hidden")
	var perimeter=0.0
	for edge in outline.edges([z.polygon(0,1.5),z.polygon(0,1.5)]): perimeter+=edge[0].distance_to(edge[1])
	check(absf(perimeter-2.0)<0.00001,"duplicate regions retain one outer contour: "+str(perimeter))
