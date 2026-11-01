extends RefCounted
## Versioned file boundary. Keeps imported data immutable and rejects ambiguous units.
const Core=preload("res://core/rhythm_core.gd")
const VERSION="0.13.0"
const FORMAT=6
const EASES=["linear","smooth","hold","sine_in_out","sine_in","sine_out"]
var error=""
var animation_count=0

func fail(message: String) -> Dictionary:
	error=message
	return {}

func numeric(value,low: float,high: float) -> bool:
	return (value is float or value is int) and is_finite(value) and value>=low and value<=high

func sequence(value,maximum: int) -> bool:
	return value is Array and value.size()<=maximum

func safe_name(value) -> bool:
	if not value is String or value.is_empty() or value in [".",".."]: return false
	for c in value:
		if not c in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-": return false
	return true

func parse_text(text: String) -> Dictionary:
	if text.to_utf8_buffer().size()>4*1024*1024: return fail("谱面 JSON 超过 4 MB")
	var json=JSON.new()
	if json.parse(text)!=OK: return fail("JSON 解析失败：第 %d 行 %s" % [json.get_error_line(),json.get_error_message()])
	return parse(json.data)

func load_path(path: String) -> Dictionary:
	error=""
	var f=FileAccess.open(path,FileAccess.READ)
	if f==null: return fail("无法读取谱面文件")
	if f.get_length()>4*1024*1024: return fail("谱面 JSON 超过 4 MB")
	return parse_text(f.get_as_text())

func parse(raw) -> Dictionary:
	error=""; animation_count=0
	if not raw is Dictionary: return fail("谱面根节点必须是对象")
	var version=raw.get("version",0)
	if not numeric(version,1,FORMAT) or version!=int(version): return fail("不支持的谱面版本，请更新客户端")
	var factor=1.0
	if version==6:
		if raw.get("coordinateScale")!=100: return fail("v6 必须声明 coordinateScale=100")
		var minimum=raw.get("minReaderVersion")
		if not minimum is String: return fail("缺少最低读取版本")
		var parts=minimum.split("."); var current=VERSION.split("."); var compared=0
		if parts.size()!=3: return fail("最低版本号无效")
		for i in range(3):
			if not parts[i].is_valid_int() or int(parts[i])<0: return fail("最低版本号无效")
			if compared==0: compared=signi(int(parts[i])-int(current[i]))
		if compared>0: return fail("此谱面需要客户端 "+minimum)
		factor=0.01
	elif raw.get("coordinateScale",1)!=1: return fail("旧格式的坐标单位冲突")
	var duration=raw.get("duration"); var bpm=raw.get("bpm"); var offset=raw.get("offset",0.0)
	if not numeric(duration,0.001,86400) or not numeric(bpm,20,400) or not numeric(offset,-600,600): return fail("歌曲时间、BPM 或偏移无效")
	if not safe_name(raw.get("audio")) or raw.audio=="chart.json": return fail("歌曲文件名无效")
	var lines=[]; var line_map={}
	if version>=3:
		if not sequence(raw.get("lines"),64) or raw.lines.is_empty(): return fail("需要 1–64 条轨迹线")
		for item in raw.lines:
			if not item is Dictionary or not safe_name(item.get("id")) or item.id.length()>64 or "." in item.id or line_map.has(item.id): return fail("轨迹 ID 无效或重复")
			var frames=parse_frames(item.get("frames"),bpm,factor,false)
			if not error.is_empty(): return {}
			if frames.is_empty(): return fail("轨迹缺少初始帧")
			var events={}
			if item.has("events"):
				if version<4 or not item.events is Dictionary: return fail("轨迹时间条需要 v4 格式")
				for prop in item.events:
					if not prop in ["x","alpha","speed"]: return fail("未知轨迹变化栏")
					events[prop]=parse_events(item.events[prop],bpm,0.0,8.0 if prop=="speed" else 1.0,factor if prop=="x" else 1.0)
					if not error.is_empty(): return {}
			var line=Core.Track.new({"id":item.id,"name":str(item.get("name",item.id)).left(200),"frames":frames,"events":events})
			lines.append(line); line_map[item.id]=line
	if not sequence(raw.get("notes"),20000): return fail("最多 20000 个音符")
	var notes=[]
	for item in raw.notes:
		if not item is Dictionary or not item.get("type") in ["tap","hold"]: return fail("仅支持 Tap / Hold")
		var t=item.get("time"); var length=item.get("duration",0.0) if item.type=="hold" else 0.0
		if not numeric(t,0,86400) or not numeric(length,0.000000001 if item.type=="hold" else 0,86400): return fail("音符时间或 Hold 长度无效")
		if t+offset< -0.001 or t+length+offset>duration+0.05: return fail("音符超出歌曲时长")
		var line
		if version>=3:
			line=line_map.get(item.get("line"))
			if line==null: return fail("音符引用不存在的轨迹")
		else:
			if not numeric(item.get("x"),0,1): return fail("旧音符位置无效")
			var id="legacy_"+str(item.x)
			if not line_map.has(id):
				line_map[id]=Core.Track.new({"id":id,"frames":[{"time":0.0,"x":item.x,"alpha":1.0,"ease":"linear"}]}); lines.append(line_map[id])
			line=line_map[id]
		notes.append({"id":notes.size(),"time":float(t),"duration":float(length),"line":line})
	notes.sort_custom(func(a,b):return a.time<b.time)
	if not sequence(raw.get("sections"),5000) or raw.sections.is_empty(): return fail("区域段落缺失或过多")
	var sections=[]; var previous=0.0
	for item in raw.sections:
		if not item is Dictionary or not numeric(item.get("start"),0,86400) or not numeric(item.get("end"),0,86400): return fail("区域段落时间无效")
		if absf(item.start-previous)>0.0001 or item.end<=item.start: return fail("区域段落必须连续且不重叠")
		previous=item.end
		if not sequence(item.get("rects"),32): return fail("每段最多 32 个区域")
		var motions=item.get("motions",[]); var events=item.get("events",[])
		if not motions is Array or (not motions.is_empty() and motions.size()!=item.rects.size()): return fail("运动帧数量与矩形不匹配")
		if not events is Array or (not events.is_empty() and (version<5 or events.size()!=item.rects.size())): return fail("区域时间条需要 v5 以上且数量对应")
		var zones=[]
		for i in range(item.rects.size()):
			var r=item.rects[i]
			if not sequence(r,4) or r.size()!=4: return fail("区域矩形需要四个坐标")
			var rect=[]
			for value in r:
				if not numeric(value,0,1.0/factor): return fail("区域坐标越界")
				rect.append(value*factor)
			if rect[2]<=rect[0] or rect[3]<=rect[1]: return fail("区域长宽必须大于零")
			var frames=parse_frames(motions[i] if not motions.is_empty() else [],bpm,factor,true)
			if not error.is_empty(): return {}
			if version==1 and not frames.is_empty(): return fail("运动帧需要 v2 格式")
			animation_count+=frames.size()
			var channels={}
			if not events.is_empty():
				if not events[i] is Dictionary: return fail("区域变化栏无效")
				for prop in events[i]:
					if not prop in ["center","angle","width","height"]: return fail("未知区域变化栏")
					var low=-2.0 if prop=="center" else (-36000.0 if prop=="angle" else 0.001)
					var high=3.0 if prop=="center" else (36000.0 if prop=="angle" else 4.0)
					channels[prop]=parse_events(events[i][prop],bpm,low,high,1.0 if prop=="angle" else factor,prop=="center")
					if not error.is_empty(): return {}
					animation_count+=channels[prop].size()
			if animation_count>20000: return fail("区域动画超过 20000 条")
			zones.append(Core.Zone.new(rect,frames,channels))
		sections.append({"start":float(item.start),"end":float(item.end),"label":str(item.get("label","区域")).left(200),"zones":zones})
	if previous<duration-offset: return fail("区域未覆盖整首歌曲")
	return {"title":str(raw.get("title","Untitled")).left(200),"artist":str(raw.get("artist","")).left(200),"difficulty":str(raw.get("difficulty","CUSTOM")).left(200),"duration":float(duration),"offset":float(offset),"bpm":float(bpm),"audio":raw.audio,"version":int(version),"app_version":str(raw.get("appVersion","0.11.0")),"lines":lines,"notes":notes,"sections":sections}

func parse_frames(raw,bpm: float,factor: float,region: bool) -> Array:
	if not sequence(raw,256): error="每条轨迹或区域最多 256 帧"; return []
	var result=[]; var last=-1.0
	for f in raw:
		if not f is Dictionary or not numeric(f.get("time"),0,600000*60/bpm) or f.time<=last: error="关键帧时间无效或未递增"; return []
		var ease=f.get("ease","linear")
		if not ease in ["linear","smooth","hold"]: error="关键帧缓动无效"; return []
		last=f.time
		if region:
			if not numeric(f.get("x"),-2/factor,2/factor) or not numeric(f.get("y"),-2/factor,2/factor) or not numeric(f.get("angle"),-36000,36000): error="区域运动帧无效"; return []
			result.append({"time":float(f.time),"x":f.x*factor,"y":f.y*factor,"angle":float(f.angle),"ease":ease})
		else:
			if not numeric(f.get("x"),0,1/factor) or not numeric(f.get("alpha"),0,1): error="轨迹位置或透明度无效"; return []
			result.append({"time":float(f.time),"x":f.x*factor,"alpha":float(f.alpha),"ease":ease})
	return result

func parse_events(raw,bpm: float,low: float,high: float,factor: float,vector: bool=false) -> Array:
	if not sequence(raw,256): error="每栏最多 256 个时间条"; return []
	var result=[]; var previous=0.0
	for e in raw:
		if not e is Dictionary or not numeric(e.get("start"),0,600000*60/bpm) or not numeric(e.get("end"),0,600000*60/bpm): error="时间条时间无效"; return []
		if e.end<=e.start or e.start<previous-0.00000001: error="时间条重叠或范围无效"; return []
		var ease=e.get("ease","linear")
		if not ease in EASES: error="未知缓动类型"; return []
		var a=e.get("from"); var b=e.get("to")
		if vector:
			if not sequence(a,2) or not sequence(b,2) or a.size()!=2 or b.size()!=2: error="中心点需要 [X,Y]"; return []
			for value in a+b:
				if not numeric(value,low/factor,high/factor): error="区域时间条数值越界"; return []
			a=[a[0]*factor,a[1]*factor]; b=[b[0]*factor,b[1]*factor]
		else:
			if not numeric(a,low/factor,high/factor) or not numeric(b,low/factor,high/factor): error="时间条数值越界"; return []
			a*=factor; b*=factor
		result.append({"start":float(e.start),"end":float(e.end),"from":a,"to":b,"ease":ease}); previous=e.end
	return result
