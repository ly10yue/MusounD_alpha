extends RefCounted
var config=ConfigFile.new()
var settings={"approach":2.2,"offset":0,"hit_sounds":true,"hit_volume":1.0,"guide":true}
var selected="demo"
var legacy={}
var legacy_prefs

func _init():
	config.load("user://profile.cfg")
	for key in settings: settings[key]=config.get_value("settings",key,settings[key])
	settings.approach=clampf(float(settings.approach),0.6,4.0)
	settings.offset=clampi(int(settings.offset),-300,300)
	settings.hit_volume=clampf(float(settings.hit_volume),0,2)
	selected=str(config.get_value("library","selected","demo"))
	load_legacy()

func save():
	for key in settings: config.set_value("settings",key,settings[key])
	config.set_value("library","selected",selected)
	return config.save("user://profile.cfg")

func java_hash(value: String) -> int:
	var h=0
	for c in range(value.to_utf16_buffer().size()/2):
		h=(31*h+value.to_utf16_buffer().decode_u16(c*2))&0xffffffff
	return h-0x100000000 if h>=0x80000000 else h

func legacy_key(song: Dictionary) -> String:
	if song.id=="demo": return "best"
	var path=ProjectSettings.globalize_path(song.path)
	if song.id=="at-flow": path=OS.get_user_data_dir().path_join("bundled/at-flow/chart.json")
	return "best_"+str(java_hash(path))

func legacy_score(key: String) -> int:
	if not legacy.has(key):
		legacy[key]=int(legacy_prefs.getInt(key,0)) if legacy_prefs!=null else 0
	return int(legacy[key])

func best(song: Dictionary) -> int:
	var result=maxi(int(config.get_value("scores",song.id,0)),legacy_score(legacy_key(song)))
	if OS.get_name()=="Android" and song.id!="demo":
		var path=ProjectSettings.globalize_path(song.path)
		if song.id=="at-flow": path=OS.get_user_data_dir().path_join("bundled/at-flow/chart.json")
		for alias in [path.replace("/data/data/","/data/user/0/"),path.replace("/data/user/0/","/data/data/")]:
			result=maxi(result,legacy_score("best_"+str(java_hash(alias))))
	return result

func record(song: Dictionary,score: int):
	if score>best(song): config.set_value("scores",song.id,score); save()

func load_legacy():
	if OS.get_name()!="Android" or not Engine.has_singleton("AndroidRuntime"): return
	var runtime=Engine.get_singleton("AndroidRuntime")
	var context=runtime.getApplicationContext()
	if context==null: return
	legacy_prefs=context.getSharedPreferences("interval",0)
	legacy["best"]=int(legacy_prefs.getInt("best",0))
	if not config.has_section("library"):
		var last=str(context.getSharedPreferences("MainActivity",0).getString("chart",""))
		if last.ends_with("bundled/at-flow/chart.json"): selected="at-flow"
		elif "/charts/" in last: selected="user://charts/"+last.split("/charts/",true,1)[1]
	if config.has_section("settings"): return
	settings.approach=clampf(float(legacy_prefs.getFloat("approach",2.2)),0.6,4.0)
	settings.offset=clampi(int(legacy_prefs.getInt("offset",0)),-300,300)
	settings.hit_sounds=bool(legacy_prefs.getBoolean("hitSounds",true))
	settings.guide=bool(legacy_prefs.getBoolean("zoneGuide",true)); save()
