extends SceneTree
var failures=[]
func _initialize(): call_deferred("run")
func check(value: bool,message: String):
	if not value: failures.append(message); printerr("FAIL: "+message)
func run():
	var host=load("res://main.tscn").instantiate(); root.add_child(host)
	while host.scanning: await process_frame
	check(host.songs.size()>=2,"library ready")
	var original=host.ui.get_node("Title/Start").get_instance_id()
	host.ui.get_node("Title/Start").pressed.emit()
	check(host.state==host.SONGS and host.ui.get_node("Songs").visible,"scene button opens library")
	host.ui.get_node("Songs/Song1").pressed.emit()
	check(host.selected==1,"song card binds its own row")
	host.ui.get_node("Songs/Settings").pressed.emit()
	check(host.state==host.SETTINGS,"settings button")
	var before=host.profile.settings.approach
	host.ui.get_node("Settings/ApproachMinus").pressed.emit()
	check(is_equal_approx(host.profile.settings.approach,maxf(0.6,before-0.2)),"settings signal updates model")
	host.ui.get_node("Settings/Back").pressed.emit()
	check(host.state==host.SONGS,"settings return page")
	host.change_state(host.TITLE); host.ui.get_node("Title/Help").pressed.emit()
	check(host.state==host.HELP,"help button")
	host.ui.get_node("Help/Back").pressed.emit()
	check(host.state==host.TITLE,"help returns home")
	check(host.ui.get_node("Title/Start").get_instance_id()==original,"page nodes persist across state changes")
	for width in [800.0,1066.6667]:
		host.ui.size=Vector2(width,600); await process_frame
		check(host.ui.get_node("Songs/Import").position.x>width-220,"right anchored import")
		check(host.ui.get_node("Pause/Panel").position.x>=0,"centered overlay fits")
		check(host.ui.get_node("Songs/Play").get_rect().end.x<=width-47,"song details fit")
	print("Scene UI checks: ",failures.size()," failures")
	host.queue_free(); await process_frame; quit(0 if failures.is_empty() else 1)
