extends Control
const Core=preload("res://core/rhythm_core.gd")
const Outline=preload("res://core/region_outline.gd")
const BG=Color("10191b")
const INK=Color("edf5eb")
const MINT=Color("a6f4cf")
const ORANGE=Color("ffb682")
const MUTED=Color("8da5a4")
var chart={}
var engine
var time=0.0
var approach=2.2
var guide=true
var autoplay=false
var font: Font
var w=1066.6667
var scale_factor=1.0
var origin=Vector2.ZERO
var effects=[]
var feedback=""
var timing=""
var feedback_until=0

func _ready(): mouse_filter=Control.MOUSE_FILTER_IGNORE

func layout():
	var available=get_viewport_rect().size
	scale_factor=minf(available.y/600.0,available.x/800.0)
	w=minf(1066.6667,available.x/scale_factor)
	origin=(available-Vector2(w,600)*scale_factor)/2.0
	if engine!=null: engine.field_aspect=(w-96)/600.0

func point(position: Vector2) -> Vector2:
	var p=(position-origin)/scale_factor
	return Vector2((p.x-48)/(w-96),p.y/600.0)

func nx(x: float) -> float: return 48+x*(w-96)
func screen_point(p: Vector2) -> Vector2: return Vector2(nx(p.x),p.y*600)
func ny(line,at: float,travel: float) -> float: return 480-line.distance(time,at)*travel

func text(value: String,x: float,y: float,size: int=14,color: Color=INK,align: int=0,max_width: float=-1):
	if font==null: return
	if max_width>0:
		while value.length()>1 and font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x>max_width:
			value=value.left(value.length()-2)+"…"
	var width=font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
	draw_string(font,Vector2(x-width*align/2.0,y),value,HORIZONTAL_ALIGNMENT_LEFT,-1,size,color)

func judged(note: Dictionary,grade: String,delta: float):
	effects.append({"x":engine.hit_x(note,engine.time),"born":Time.get_ticks_msec(),"miss":grade=="MISS"})
	feedback=grade; feedback_until=Time.get_ticks_msec()+650
	timing="" if grade in ["MISS","HOLD"] else ("EARLY " if delta<0 else "LATE ")+str(roundi(absf(delta)*1000))+" ms"

func _draw():
	if engine==null or chart.is_empty(): return
	layout(); draw_rect(get_viewport_rect(),Color("080e10")); draw_set_transform(origin,0,Vector2.ONE*scale_factor)
	draw_rect(Rect2(0,0,w,600),BG)
	var field=w-96; var travel=(480.0-105.0)/approach
	draw_rect(Rect2(48,100,field,465),Color("090f11"))
	# A clip control would clip to the same field rectangle; polygon drawing is clamped below via a canvas item.
	var section=engine.section_at(maxf(0,time)); var polys=[]
	if section!=null:
		for zone in section.zones: polys.append(zone.polygon(maxf(0,time),engine.field_aspect))
		for poly in polys:
			var points=PackedVector2Array()
			for p in poly: points.append(screen_point(p))
			var clip=Geometry2D.intersect_polygons(points,PackedVector2Array([Vector2(48,100),Vector2(w-48,100),Vector2(w-48,565),Vector2(48,565)]))
			for p in clip: draw_colored_polygon(p,Color("1a2a29") if guide else BG)
		for edge in Outline.edges(polys): line_clipped(screen_point(edge[0]),screen_point(edge[1]),Color("608b76"),1.5)
	var visible={}
	for track in engine.lines:
		var lo=track.horizon(time,(480.0-600.0)/travel,minf(0,time))
		var hi=track.horizon(time,(480.0-95.0)/travel,chart.duration-chart.offset+3)
		visible[track.id]=Vector2(lo,hi)
		var alpha=track.pose(time).y*0.55
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
		if path.size()>1: draw_polyline(path,Color(0.56,0.81,0.73,alpha),2,true)
		draw_circle(Vector2(nx(track.x(time)),480),4,Color(0.56,0.81,0.73,alpha))
	for n in engine.notes:
		var limits=visible[n.line.id]
		if n.time>limits.y or n.state in [Core.DONE,Core.LOST] or n.time+n.duration<limits.x: continue
		var start=maxf(n.time,time) if n.state==Core.HELD else n.time
		var x=nx(n.line.x(start)); var width=field*0.092; var head=ny(n.line,start,travel)
		if n.duration>0:
			var lo=maxf(start,limits.x); var hi=minf(n.time+n.duration,limits.y)
			if hi<lo: continue
			var samples=n.line.samples(lo,hi); var polygon=PackedVector2Array()
			for p in samples: polygon.append(Vector2(nx(p.y)-width/2.0,ny(n.line,p.x,travel)))
			for i in range(samples.size()-1,-1,-1):
				var p=samples[i]; polygon.append(Vector2(nx(p.y)+width/2.0,ny(n.line,p.x,travel)))
			var clipped=Geometry2D.intersect_polygons(polygon,PackedVector2Array([Vector2(48,100),Vector2(w-48,100),Vector2(w-48,565),Vector2(48,565)]))
			for poly in clipped:
				if poly.size()>2: draw_colored_polygon(poly,Color("786247") if n.state==Core.HELD else Color("4f4436"))
			for i in range(polygon.size()): line_clipped(polygon[i],polygon[(i+1)%polygon.size()],ORANGE,1.5)
			var tail=ny(n.line,n.time+n.duration,travel)
			if tail>=100 and tail<565: draw_rect(Rect2(nx(n.line.x(n.time+n.duration))-width/2.0-3,tail-2,width+6,5),ORANGE)
		if head>=100 and head<565:
			draw_rect(Rect2(x-width/2.0-3,head-5,width+6,10),ORANGE if n.duration>0 else MINT)
			draw_rect(Rect2(x-width/2.0+5,head-2,width-10,2),Color("edf9f3"))
	draw_line(Vector2(48,480),Vector2(w-48,480),INK,2)
	text("JUDGEMENT / FIXED",48,504,9,MUTED)
	var now=Time.get_ticks_msec()
	for i in range(effects.size()-1,-1,-1):
		var effect=effects[i]; var age=(now-effect.born)/420.0
		if age>1: effects.remove_at(i); continue
		var color=ORANGE if effect.miss else MINT; color.a=(1-age)*0.75
		draw_arc(Vector2(nx(effect.x),480),15+age*37,0,TAU,40,color,2,true)
	for p in engine.pointers.values():
		draw_circle(screen_point(p),23,Color(0.65,0.95,0.81,0.25)); draw_circle(screen_point(p),4,MINT)
	if engine.combo>1: text(str(engine.combo),w/2,183,42,INK,1); text("COMBO",w/2,204,10,MUTED,1)
	if now<feedback_until: text(feedback,w/2,252,19,ORANGE if feedback=="MISS" else MINT,1); text(timing,w/2,273,10,MUTED,1)
	draw_rect(Rect2(0,0,w,88),BG)
	text(chart.title,48,35,17,INK,0,w*0.36); text(chart.artist+" / "+chart.difficulty,48,58,10,MUTED,0,w*0.36)
	text("AUTO / 演示" if autoplay else "LIVE / 演奏",w/2,35,11,ORANGE if autoplay else MINT,1)
	text("%.2f%%" % engine.accuracy(),w/2,58,12,MUTED,1); text("%07d" % engine.score(),w-110,42,27,INK,2)
	draw_rect(Rect2(48,77,field,2),Color("304244")); draw_rect(Rect2(48,77,field*clampf((time+chart.offset)/chart.duration,0,1),2),MINT)
	text(section.label if section!=null else "准备",48,584,12,MINT,0,w-280)
	text(format_time(maxf(0,time+chart.offset))+" / "+format_time(chart.duration),w-48,584,11,MUTED,2)
	if section!=null and section.end-time>0 and section.end-time<2.2:
		var next=engine.section_at(section.end+0.001)
		if next!=null:
			draw_rect(Rect2(w/2-172,521,344,33),Color("3c3c2a")); text("即将切换 → "+next.label,w/2,543,13,ORANGE,1,325)
	draw_set_transform(Vector2.ZERO)

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
	draw_line(a+d*lo,a+d*hi,color,width,true)

static func format_time(value: float) -> String:
	return "%02d:%02d" % [int(value)/60,int(value)%60]
