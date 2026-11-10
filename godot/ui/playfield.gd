extends Control
const Core=preload("res://core/rhythm_core.gd")
const Outline=preload("res://core/region_outline.gd")
var chart={}
var engine
var time=0.0
var approach=2.2
var guide=true
var autoplay=false
@export var skin: Resource
@onready var canvas=$Canvas
@onready var hud=$Canvas/HUD
var geometry
var w=1066.6667
var scale_factor=1.0
var origin=Vector2.ZERO
var effects=[]
var feedback=""
var timing=""
var feedback_until=0

func _ready(): layout()

func layout():
	var available=get_viewport_rect().size
	scale_factor=minf(available.y/600.0,available.x/800.0)
	w=minf(1066.6667,available.x/scale_factor)
	origin=(available-Vector2(w,600)*scale_factor)/2.0
	if engine!=null: engine.field_aspect=(w-96)/600.0
	if is_node_ready():
		canvas.position=origin; canvas.scale=Vector2.ONE*scale_factor; canvas.size=Vector2(w,600)

func point(position: Vector2) -> Vector2:
	var p=(position-origin)/scale_factor
	return Vector2((p.x-48)/(w-96),p.y/600.0)

func nx(x: float) -> float: return 48+x*(w-96)
func screen_point(p: Vector2) -> Vector2: return Vector2(nx(p.x),p.y*600)
func ny(line,at: float,travel: float) -> float: return 480-line.distance(time,at)*travel

func judged(note: Dictionary,grade: String,delta: float):
	effects.append({"x":engine.hit_x(note,engine.time),"born":Time.get_ticks_msec(),"miss":grade=="MISS"})
	feedback=grade; feedback_until=Time.get_ticks_msec()+650
	timing="" if grade in ["MISS","HOLD"] else ("EARLY " if delta<0 else "LATE ")+str(roundi(absf(delta)*1000))+" ms"

func _process(_delta):
	if engine==null or chart.is_empty() or not is_visible_in_tree(): return
	layout()
	for layer in ["Regions","Tracks","Notes","Effects"]: canvas.get_node(layer).begin_frame()
	geometry=$Canvas/Regions
	var field=w-96; var travel=(480.0-105.0)/approach
	# Clip dynamic polygons/edges to the same logical playfield as input layout.
	var section=engine.section_at(maxf(0,time)); var polys=[]
	if section!=null:
		for zone in section.zones:
			var poly=zone.polygon(maxf(0,time),engine.field_aspect)
			if not poly.is_empty(): polys.append(poly)
		for poly in polys:
			var points=PackedVector2Array()
			for p in poly: points.append(screen_point(p))
			var clip=Geometry2D.intersect_polygons(points,PackedVector2Array([Vector2(48,100),Vector2(w-48,100),Vector2(w-48,565),Vector2(48,565)]))
			for p in clip: geometry.polygon(p,skin.region_fill if guide else skin.background)
		for edge in Outline.edges(polys): line_clipped(screen_point(edge[0]),screen_point(edge[1]),skin.region_edge,skin.outline_width)
	geometry=$Canvas/Tracks
	var visible={}
	for track in engine.lines:
		var lo=track.horizon(time,(480.0-600.0)/travel,minf(0,time))
		var hi=track.horizon(time,(480.0-95.0)/travel,chart.duration-chart.offset+3)
		visible[track.id]=Vector2(lo,hi)
		var alpha=track.pose(time).y*skin.track.a
		if alpha<=0: continue
		var points=track.samples(time,hi)
		var path=PackedVector2Array()
		for point in points:
			var pixel=Vector2(nx(point.y),ny(track,point.x,travel))
			if pixel.y<100:
				if not path.is_empty():
					var last=path[path.size()-1]
					if last.y>100: path.append(last.lerp(pixel,(last.y-100)/(last.y-pixel.y)))
				break
			path.append(pixel)
		if path.size()>1: geometry.polyline(path,Color(skin.track,alpha),skin.track_width,true)
		geometry.circle(Vector2(nx(track.x(time)),480),4,Color(skin.track,alpha))
	geometry=$Canvas/Notes
	for n in engine.notes:
		var limits=visible[n.line.id]
		if n.time>limits.y or n.state in [Core.DONE,Core.LOST] or n.time+n.duration<limits.x: continue
		var start=maxf(n.time,time) if n.state==Core.HELD else n.time
		var x=nx(n.line.x(start)); var width=field*skin.note_width_ratio; var head=ny(n.line,start,travel)
		if n.duration>0:
			var lo=maxf(start,limits.x); var hi=minf(n.time+n.duration,limits.y)
			if hi<lo: continue
			var samples=n.line.samples(lo,hi); var polygon=PackedVector2Array()
			for p in samples: polygon.append(Vector2(nx(p.y)-width/2.0,ny(n.line,p.x,travel)))
			for i in range(samples.size()-1,-1,-1):
				var p=samples[i]; polygon.append(Vector2(nx(p.y)+width/2.0,ny(n.line,p.x,travel)))
			var clipped=Geometry2D.intersect_polygons(polygon,PackedVector2Array([Vector2(48,100),Vector2(w-48,100),Vector2(w-48,565),Vector2(48,565)]))
			for poly in clipped:
				if poly.size()>2: geometry.polygon(poly,skin.hold_active if n.state==Core.HELD else skin.hold_idle)
			for i in range(polygon.size()): line_clipped(polygon[i],polygon[(i+1)%polygon.size()],skin.hold,skin.outline_width)
			var tail=ny(n.line,n.time+n.duration,travel)
			if tail>=100 and tail<565: geometry.note_head(Vector2(nx(n.line.x(n.time+n.duration)),tail),width,skin.hold,true)
		if head>=100 and head<565:
			geometry.note_head(Vector2(x,head),width,skin.hold if n.duration>0 else skin.tap)
	geometry=$Canvas/Effects
	var now=Time.get_ticks_msec()
	for i in range(effects.size()-1,-1,-1):
		var effect=effects[i]; var age=(now-effect.born)/skin.hit_duration_ms
		if age>1: effects.remove_at(i); continue
		var color=skin.hold if effect.miss else skin.tap; color.a=(1-age)*0.75
		geometry.arc(Vector2(nx(effect.x),480),15+age*37,0,TAU,40,color,2,true)
	for p in engine.pointers.values():
		geometry.circle(screen_point(p),23,skin.pointer); geometry.circle(screen_point(p),4,skin.tap)
	for layer in ["Regions","Tracks","Notes","Effects"]: canvas.get_node(layer).end_frame()
	refresh_hud(section,now)

func refresh_hud(section,now: int):
	hud.get_node("Title").text=chart.title
	hud.get_node("Artist").text=chart.artist+" / "+chart.difficulty
	hud.get_node("Mode").text="AUTO / 演示" if autoplay else "LIVE / 演奏"
	hud.get_node("Accuracy").text="%.2f%%" % engine.accuracy()
	hud.get_node("Score").text="%07d" % engine.score()
	hud.get_node("Combo").text=str(engine.combo) if engine.combo>1 else ""
	hud.get_node("ComboCaption").visible=engine.combo>1
	hud.get_node("Feedback").text=feedback if now<feedback_until else ""
	hud.get_node("Feedback").modulate=skin.hold if feedback=="MISS" else skin.tap
	hud.get_node("Timing").text=timing if now<feedback_until else ""
	hud.get_node("Progress").scale.x=clampf((time+chart.offset)/chart.duration,0,1)
	hud.get_node("Section").text=section.label if section!=null else "准备"
	hud.get_node("Time").text=format_time(maxf(0,time+chart.offset))+" / "+format_time(chart.duration)
	var next=engine.section_at(section.end+0.001) if section!=null and section.end-time>0 and section.end-time<2.2 else null
	hud.get_node("NextPanel").visible=next!=null; hud.get_node("Next").visible=next!=null
	if next!=null: hud.get_node("Next").text="即将切换 → "+next.label

func line_clipped(a: Vector2,b: Vector2,color: Color,width: float):
	# Liang–Barsky clipping keeps out-of-bounds curves away from the HUD and margins.
	var d=b-a; var lo=0.0; var hi=1.0
	var p=[-d.x,d.x,-d.y,d.y]; var q=[a.x-48,w-48-a.x,a.y-100,565-a.y]
	for i in range(4):
		if absf(p[i])<0.000000001:
			if q[i]<0: return
		else:
			var r=q[i]/p[i]
			if p[i]<0: lo=maxf(lo,r)
			else: hi=minf(hi,r)
			if lo>hi: return
	geometry.line(a+d*lo,a+d*hi,color,width,true)

static func format_time(value: float) -> String:
	return "%02d:%02d" % [int(value)/60,int(value)%60]
