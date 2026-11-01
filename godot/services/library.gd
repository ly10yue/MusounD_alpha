extends RefCounted
const Loader=preload("res://core/chart_loader.gd")
var error=""
const MAX_TOTAL=260*1024*1024

func audio_stream(path: String):
	if path.begins_with("res://"):
		return load(path)
	match path.get_extension().to_lower():
		"ogg": return AudioStreamOggVorbis.load_from_file(path)
		"mp3": return AudioStreamMP3.load_from_file(path)
		"wav": return AudioStreamWAV.load_from_file(path)
	error="歌曲格式不支持，请在制谱器中转换为 Ogg 后重新导出"
	return null

func song(path: String,id: String="") -> Dictionary:
	var loader=Loader.new(); var chart=loader.load_path(path)
	if chart.is_empty(): error=loader.error; return {}
	var audio=path.get_base_dir().path_join(chart.audio)
	if not FileAccess.file_exists(audio) and not (audio.begins_with("res://") and ResourceLoader.exists(audio)): error="歌曲文件不存在"; return {}
	var holds=0
	for n in chart.notes:
		if n.duration>0: holds+=1
	return {"id":id if not id.is_empty() else path,"path":path,"audio":audio,"chart":chart,"holds":holds,"builtin":path.begins_with("res://")}

func scan() -> Dictionary:
	var songs=[]; var unavailable=0
	for name in ["demo","at-flow"]:
		var item=song("res://assets/"+name+".json",name)
		if item.is_empty(): unavailable+=1
		else: songs.append(item)
	DirAccess.make_dir_recursive_absolute("user://charts")
	var dirs=DirAccess.get_directories_at("user://charts")
	dirs.sort()
	for folder in dirs:
		var item=song("user://charts/"+folder+"/chart.json")
		if item.is_empty(): unavailable+=1
		else: songs.append(item)
	return {"songs":songs,"unavailable":unavailable}

func clear_folder(folder: String):
	# Only our newly-created, flat import directory is passed here.
	for file in DirAccess.get_files_at(folder): DirAccess.remove_absolute(folder.path_join(file))
	DirAccess.remove_absolute(folder)

func import_package(source: String) -> Dictionary:
	error=""
	DirAccess.make_dir_recursive_absolute("user://imports")
	var cached="user://imports/incoming.interval"
	var input=FileAccess.open(source,FileAccess.READ)
	if input==null: error="无法读取所选文件"; return {}
	if input.get_length()>MAX_TOTAL: error="谱面包过大"; return {}
	var output=FileAccess.open(cached,FileAccess.WRITE)
	if output==null: error="无法创建导入缓存"; return {}
	var total=0
	while not input.eof_reached():
		var buffer=input.get_buffer(65536)
		if buffer.is_empty(): break
		total+=buffer.size()
		if total>MAX_TOTAL: input.close(); output.close(); DirAccess.remove_absolute(cached); error="谱面包超过 260 MB"; return {}
		output.store_buffer(buffer)
	input.close(); output.close()
	var result=unpack(cached)
	DirAccess.remove_absolute(cached)
	return result

func inspect_zip(path: String) -> Dictionary:
	var f=FileAccess.open(path,FileAccess.READ)
	if f==null or f.get_length()<22: error="不是完整的 .interval 文件"; return {}
	var length=f.get_length(); var start=maxi(0,length-65557)
	f.seek(start); var tail=f.get_buffer(length-start); var e=-1
	for i in range(tail.size()-22,-1,-1):
		if tail.decode_u32(i)==0x06054b50 and i+22+tail.decode_u16(i+20)==tail.size(): e=i; break
	if e<0: error="ZIP 文件尾损坏"; return {}
	var count=tail.decode_u16(e+10); var offset=tail.decode_u32(e+16); var size=tail.decode_u32(e+12)
	if count<1 or count>8 or tail.decode_u16(e+4)!=0 or tail.decode_u16(e+6)!=0 or tail.decode_u16(e+8)!=count or offset+size!=start+e: error="不支持分卷、ZIP64 或过多文件"; return {}
	f.seek(offset); var names={}; var total=0
	for _i in range(count):
		var header=f.get_buffer(46)
		if header.size()!=46 or header.decode_u32(0)!=0x02014b50: error="ZIP 目录损坏"; return {}
		var n=header.decode_u16(28); var extra=header.decode_u16(30); var comment=header.decode_u16(32)
		var name=f.get_buffer(n).get_string_from_utf8(); var bytes=header.decode_u32(24)
		if not Loader.new().safe_name(name) or names.has(name): error="谱面包文件名无效、包含目录或重复"; return {}
		if header.decode_u16(8)&1 or not header.decode_u16(10) in [0,8]: error="不支持加密或此种压缩格式"; return {}
		total+=bytes
		if bytes>(4*1024*1024 if name=="chart.json" else 256*1024*1024) or total>MAX_TOTAL: error="解压文件过大"; return {}
		names[name]=bytes; f.seek(f.get_position()+extra+comment)
	if f.get_position()!=offset+size or not names.has("chart.json"): error="谱面包根目录缺少 chart.json"; return {}
	return names

func unpack(path: String) -> Dictionary:
	var sizes=inspect_zip(path)
	if sizes.is_empty(): return {}
	var zip=ZIPReader.new()
	if zip.open(path)!=OK: error="无法打开压缩包"; return {}
	var data=zip.read_file("chart.json")
	if data.size()!=sizes["chart.json"]: zip.close(); error="谱面文件损坏"; return {}
	var loader=Loader.new(); var chart=loader.parse_text(data.get_string_from_utf8())
	if chart.is_empty(): zip.close(); error=loader.error; return {}
	if not sizes.has(chart.audio): zip.close(); error="包内缺少歌曲 "+chart.audio; return {}
	var bytes=zip.read_file(chart.audio); zip.close()
	if bytes.size()!=sizes[chart.audio]: error="歌曲文件损坏"; return {}
	var folder="user://charts/"+str(Time.get_unix_time_from_system()).replace(".","_")+"_"+str(Time.get_ticks_usec())
	if DirAccess.make_dir_recursive_absolute(folder)!=OK: error="无法创建曲库目录"; return {}
	var f=FileAccess.open(folder.path_join(chart.audio),FileAccess.WRITE)
	if f==null: clear_folder(folder); error="歌曲保存失败，存储空间可能不足"; return {}
	f.store_buffer(bytes); var status=f.get_error(); f.close()
	if status!=OK: clear_folder(folder); error="歌曲写入失败"; return {}
	var audio=audio_stream(folder.path_join(chart.audio))
	if audio==null or audio.get_length()<=0: clear_folder(folder); error="歌曲解码失败，请重新导出 Ogg"; return {}
	f=FileAccess.open(folder.path_join("chart.json"),FileAccess.WRITE)
	if f==null: clear_folder(folder); error="谱面保存失败"; return {}
	f.store_buffer(data); status=f.get_error(); f.close()
	if status!=OK: clear_folder(folder); error="谱面写入失败"; return {}
	return song(folder.path_join("chart.json"))
