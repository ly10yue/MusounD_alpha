extends RefCounted
## Pure gameplay core. Seconds and normalized geometry; no scene/audio dependencies.
const PERFECT = 0.060
const GOOD = 0.120
const MISS = 0.160
const TAIL = 0.080
const LEAVE_GRACE = 0.085
const HIT_RADIUS = 0.085
const WAIT = 0
const HELD = 1
const DONE = 2
const LOST = 3

static func eased(u: float, ease: String) -> float:
	match ease:
		"sine_in_out": return (1.0-cos(PI*u))/2.0
		"sine_in": return 1.0-cos(PI*u/2.0)
		"sine_out": return sin(PI*u/2.0)
		"smooth": return u*u*(3.0-2.0*u)
		"hold": return 0.0
	return u

static func ease_integral(u: float, ease: String) -> float:
	match ease:
		"sine_in_out": return u/2.0-sin(PI*u)/(2.0*PI)
		"sine_in": return u-2.0/PI*sin(PI*u/2.0)
		"sine_out": return 2.0/PI*(1.0-cos(PI*u/2.0))
		"smooth": return u*u*u-u*u*u*u/2.0
		"hold": return 0.0
	return u*u/2.0

class EventCurve:
	extends RefCounted
	const Ease=preload("res://core/easing.gd")
	var base: float
	var events: Array
	var prefix: Array = []
	func _init(initial: float, items: Array = []):
		base=initial; events=items
		var total=0.0; var at=0.0; var last=base
		for e in events:
			total+=(e.start-at)*last; prefix.append(total)
			total+=area(e,1.0); at=e.end; last=e.to
	func index(t: float) -> int:
		var lo=0; var hi=events.size()
		while lo<hi:
			var mid=(lo+hi)/2
			if events[mid].start<=t: lo=mid+1
			else: hi=mid
		return lo-1
	func area(e: Dictionary,u: float) -> float:
		return (e.end-e.start)*(e.from*u+(e.to-e.from)*Ease.ease_integral(u,e.ease))
	func value(t: float) -> float:
		var i=index(t)
		if i<0: return base
		var e=events[i]
		if t>=e.end: return e.to
		return e.from+(e.to-e.from)*Ease.eased((t-e.start)/(e.end-e.start),e.ease)
	func primitive(t: float) -> float:
		var i=index(t)
		if i<0: return t*base
		var e=events[i]
		if t>=e.end: return prefix[i]+area(e,1.0)+(t-e.end)*e.to
		return prefix[i]+area(e,(t-e.start)/(e.end-e.start))
	func distance(now: float, arrival: float) -> float:
		return primitive(arrival)-primitive(now)
	func horizon(now: float, amount: float, limit: float) -> float:
		var target=primitive(now)+amount
		var lo=now if amount>=0 else limit
		var hi=limit if amount>=0 else now
		if target>=primitive(hi): return hi
		if target<=primitive(lo): return lo
		for _i in range(32):
			var mid=(lo+hi)/2.0
			if primitive(mid)<target: lo=mid
			else: hi=mid
		return (lo+hi)/2.0

class Track:
	extends RefCounted
	const Ease=preload("res://core/easing.gd")
	var id: String
	var name: String
	var frames: Array
	var position
	var opacity
	var speed
	func _init(data: Dictionary):
		id=data.id; name=data.get("name",id); frames=data.frames
		var channels=data.get("events",{})
		position=EventCurve.new(frames[0].x,channels.x) if channels.has("x") else null
		opacity=EventCurve.new(frames[0].alpha,channels.alpha) if channels.has("alpha") else null
		speed=EventCurve.new(1.0,channels.get("speed",[]))
	func pose(t: float) -> Vector2:
		var a=frames[0]
		var result=Vector2(a.x,a.alpha)
		if t>a.time:
			for i in range(1,frames.size()):
				var b=frames[i]
				if t<b.time:
					var u=Ease.eased((t-a.time)/(b.time-a.time),a.ease)
					result=Vector2(lerpf(a.x,b.x,u),lerpf(a.alpha,b.alpha,u)); break
				a=b; result=Vector2(a.x,a.alpha)
		if position!=null: result.x=position.value(t)
		if opacity!=null: result.y=opacity.value(t)
		return result
	func x(t: float) -> float: return pose(t).x
	func distance(now: float,arrival: float) -> float: return speed.distance(now,arrival)
	func horizon(now: float,amount: float,limit: float) -> float: return speed.horizon(now,amount,limit)
	func samples(start: float,end: float,step: float=0.035) -> Array:
		if end<start: return []
		var count=clampi(int(ceil((end-start)/step)),1,512)
		var times={}
		for i in range(count+1): times[start+(end-start)*i/count]=true
		for i in range(frames.size()):
			var at=frames[i].time
			if start<at and at<=end:
				times[at]=true
				if i>0 and frames[i-1].ease=="hold": times[maxf(start,at-0.0000001)]=true
		for curve in [position,speed]:
			if curve==null: continue
			for e in curve.events:
				for at in [e.start,e.end]:
					if start<at and at<=end:
						times[at]=true; times[maxf(start,at-0.0000001)]=true
		var keys=times.keys(); keys.sort()
		var result=[]
		for at in keys: result.append(Vector2(at,x(at)))
		return result

class Zone:
	extends RefCounted
	const Ease=preload("res://core/easing.gd")
	var rect: Array
	var frames: Array
	var curves={}
	func _init(bounds: Array,keys: Array=[],channels: Dictionary={}):
		rect=bounds; frames=keys
		var initial=geometry(0.0)
		for prop in channels:
			if prop=="center":
				for component in range(2):
					var es=[]
					for e in channels[prop]:
						var copy=e.duplicate(true); copy.from=e.from[component]; copy.to=e.to[component]; es.append(copy)
					curves["cx" if component==0 else "cy"]=EventCurve.new(initial[component],es)
			else:
				curves[prop]=EventCurve.new(initial[{"angle":2,"width":3,"height":4}[prop]],channels[prop])
	func motion(t: float) -> Array:
		if frames.is_empty(): return [0.0,0.0,0.0]
		var a=frames[0]
		if t<=a.time: return [a.x,a.y,a.angle]
		for i in range(1,frames.size()):
			var b=frames[i]
			if t<b.time:
				var u=Ease.eased((t-a.time)/(b.time-a.time),a.ease)
				return [lerpf(a.x,b.x,u),lerpf(a.y,b.y,u),lerpf(a.angle,b.angle,u)]
			a=b
		return [a.x,a.y,a.angle]
	func geometry(t: float) -> Array:
		var m=motion(t)
		var result=[(rect[0]+rect[2])/2.0+m[0],(rect[1]+rect[3])/2.0+m[1],m[2],rect[2]-rect[0],rect[3]-rect[1]]
		var props=["cx","cy","angle","width","height"]
		for i in range(5):
			if curves.has(props[i]): result[i]=curves[props[i]].value(t)
		return result
	func contains(x: float,y: float,t: float,aspect: float) -> bool:
		var v=geometry(t); var dx=(x-v[0])*aspect; var dy=y-v[1]
		var c=cos(deg_to_rad(v[2])); var s=sin(deg_to_rad(v[2]))
		return absf((c*dx+s*dy)/aspect)<=v[3]/2.0+0.0000001 and absf(-s*dx+c*dy)<=v[4]/2.0+0.0000001
	func polygon(t: float,aspect: float) -> PackedVector2Array:
		var v=geometry(t); var c=cos(deg_to_rad(v[2])); var s=sin(deg_to_rad(v[2])); var points=PackedVector2Array()
		for corner in [Vector2(-1,-1),Vector2(1,-1),Vector2(1,1),Vector2(-1,1)]:
			var dx=corner.x*v[3]/2.0*aspect; var dy=corner.y*v[4]/2.0
			points.append(Vector2(v[0]+(c*dx-s*dy)/aspect,v[1]+s*dx+c*dy))
		return points
	func touch_y(x: float,t: float,aspect: float) -> Variant:
		if x<0 or x>1: return null
		var points=polygon(t,aspect); var low=INF; var high=-INF
		for i in range(4):
			var a=points[i]; var b=points[(i+1)%4]
			if x<minf(a.x,b.x)-0.0000001 or x>maxf(a.x,b.x)+0.0000001: continue
			if absf(b.x-a.x)<0.000000001:
				if absf(x-a.x)<0.0000001: low=minf(low,minf(a.y,b.y)); high=maxf(high,maxf(a.y,b.y))
			else:
				var y=a.y+(b.y-a.y)*(x-a.x)/(b.x-a.x)
				low=minf(low,y); high=maxf(high,y)
		low=maxf(0.0,low); high=minf(1.0,high)
		return (low+high)/2.0 if low<=high+0.0000001 else null

var notes: Array
var sections: Array
var lines: Array
var pointers={}
var combo=0
var max_combo=0
var perfect=0
var good=0
var miss=0
var finished=0
var points=0.0
var absolute_error=0.0
var timed_hits=0
var time=-3.0
var field_aspect=(1066.6667-96.0)/600.0
var listener: Callable

func _init(chart: Dictionary):
	notes=chart.notes; sections=chart.sections; lines=chart.lines; reset()

func reset():
	combo=0; max_combo=0; perfect=0; good=0; miss=0; finished=0; points=0.0; absolute_error=0.0; timed_hits=0; time=-3.0; pointers.clear()
	for n in notes: n.state=WAIT; n.pointer=-1; n.outside_since=null; n.grade=""

func section_at(t: float):
	for s in sections:
		if t>=s.start and t<s.end: return s
	return null

func allowed(x: float,y: float,t: float) -> bool:
	if x<0 or x>1 or y<0 or y>1: return false
	var section=section_at(t)
	if section==null: return false
	for z in section.zones:
		if z.contains(x,y,t,field_aspect): return true
	return false

func touch_y(x: float,t: float) -> Variant:
	var section=section_at(t)
	if section!=null:
		for z in section.zones:
			var y=z.touch_y(x,t,field_aspect)
			if y!=null: return y
	return null

func hit_x(n: Dictionary,t: float) -> float:
	return n.line.x(clampf(t,n.time,n.time+n.duration) if n.duration>0 and n.state==HELD else n.time)

func score() -> int: return int(round(1000000.0*points/notes.size())) if not notes.is_empty() else 0
func accuracy() -> float: return 100.0*points/finished if finished else 100.0

func finish(n: Dictionary,grade: String,delta: float):
	if n.state==DONE or n.state==LOST: return
	n.grade=grade; n.state=LOST if grade=="MISS" else DONE; finished+=1
	if n.state==LOST: miss+=1; combo=0
	else:
		combo+=1; max_combo=maxi(max_combo,combo)
		if grade=="PERFECT": perfect+=1; points+=1.0
		else: good+=1; points+=0.65
	if listener.is_valid(): listener.call(n,grade,delta)

func down(pointer: int,x: float,y: float,t: float):
	update(t); pointers[pointer]=Vector2(x,float(y))
	if not allowed(x,y,t): return null
	for n in notes:
		if n.state==HELD and n.pointer==-1 and absf(x-hit_x(n,t))<=HIT_RADIUS:
			n.pointer=pointer; n.outside_since=null; return n
	var best=null; var delta=MISS+1.0
	for n in notes:
		if n.time>t+MISS: break
		var d=absf(t-n.time)
		if n.state==WAIT and absf(x-hit_x(n,t))<=HIT_RADIUS and d<=MISS and d<delta: best=n; delta=d
	if best==null: return null
	if delta>GOOD: finish(best,"MISS",t-best.time); return best
	timed_hits+=1; absolute_error+=delta; best.grade="PERFECT" if delta<=PERFECT else "GOOD"
	if best.duration>0:
		best.state=HELD; best.pointer=pointer
		if listener.is_valid(): listener.call(best,"HOLD",t-best.time)
	else: finish(best,best.grade,t-best.time)
	return best

func move(pointer: int,x: float,y: float,t: float):
	pointers[pointer]=Vector2(x,float(y)); update(t)

func up(pointer: int,t: float):
	update(t)
	for n in notes:
		if n.state==HELD and n.pointer==pointer: finish(n,n.grade if t>=n.time+n.duration-TAIL else "MISS",0.0)
	pointers.erase(pointer)

func cancel(t: float):
	update(t)
	for n in notes:
		if n.state==HELD: finish(n,"MISS",0.0)
	pointers.clear()

func suspend():
	pointers.clear()
	for n in notes:
		if n.state==HELD: n.pointer=-1; n.outside_since=null

func reattach(pointer: int,x: float,y: float):
	pointers[pointer]=Vector2(x,float(y))
	for n in notes:
		if n.state==HELD and n.pointer==-1 and allowed(x,y,time) and absf(x-hit_x(n,time))<=HIT_RADIUS:
			n.pointer=pointer; n.outside_since=null; return true
	return false

func update(t: float):
	time=t
	for n in notes:
		if n.time>t+MISS: break
		if n.state==WAIT and t>n.time+MISS: finish(n,"MISS",t-n.time)
		if n.state!=HELD: continue
		var p=pointers.get(n.pointer)
		var valid=p!=null and allowed(p.x,p.y,t) and absf(p.x-hit_x(n,t))<=HIT_RADIUS
		if valid:
			n.outside_since=null
			if t>=n.time+n.duration: finish(n,n.grade,0.0)
		else:
			if n.outside_since==null: n.outside_since=t
			if t-n.outside_since>LEAVE_GRACE: finish(n,"MISS",0.0)

func follow_auto(t: float):
	for n in notes:
		if n.state==HELD:
			var x=hit_x(n,t); var y=touch_y(x,t); n.pointer=10000+n.id
			if y==null: pointers.erase(n.pointer)
			else: pointers[n.pointer]=Vector2(x,float(y))

func autoplay(t: float):
	for n in notes:
		if n.time>t: break
		if n.state!=WAIT: continue
		var x=hit_x(n,n.time); var y=touch_y(x,n.time)
		if y!=null:
			follow_auto(n.time); var id=10000+n.id; down(id,x,y,n.time)
			if n.duration==0: pointers.erase(id)
	follow_auto(t); update(t)
	for id in pointers.keys():
		if id<10000: continue
		var active=false
		for n in notes:
			if n.pointer==id and n.state==HELD: active=true; break
		if not active: pointers.erase(id)

func finish_all():
	for n in notes:
		if n.state==WAIT or n.state==HELD: finish(n,"MISS",0.0)
	pointers.clear()
