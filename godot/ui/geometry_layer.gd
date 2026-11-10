extends Node2D
## Pool scene instances to avoid allocating/freeing drawing nodes every frame.
@export var rectangle_scene: PackedScene
@export var polyline_scene: PackedScene
@export var polygon_scene: PackedScene
@export var note_head_scene: PackedScene
@export var hold_tail_scene: PackedScene
var pools={}
var cursors={}
func begin_frame():
	cursors.clear()
func take(kind: String):
	if not pools.has(kind): pools[kind]=[]
	var index=int(cursors.get(kind,0)); cursors[kind]=index+1
	if index==pools[kind].size():
		var scene={"rect":rectangle_scene,"line":polyline_scene,"polygon":polygon_scene,"head":note_head_scene,"tail":hold_tail_scene}[kind]
		var item=scene.instantiate(); add_child(item); pools[kind].append(item)
	var item=pools[kind][index]; item.visible=true; move_child(item,-1)
	return item
func end_frame():
	for kind in pools:
		for i in range(int(cursors.get(kind,0)),pools[kind].size()): pools[kind][i].visible=false
func rectangle(rect: Rect2,color: Color):
	var item=take("rect"); item.position=rect.position; item.size=rect.size; item.color=color
func polyline(points: PackedVector2Array,color: Color,width: float,_antialias=true):
	if points.size()<2: return
	var item=take("line"); item.points=points; item.default_color=color; item.width=width
func line(a: Vector2,b: Vector2,color: Color,width: float=1.0,_antialias=true):
	polyline(PackedVector2Array([a,b]),color,width)
func polygon(points: PackedVector2Array,color: Color):
	if points.size()<3: return
	var item=take("polygon"); item.polygon=points; item.color=color
func arc(center: Vector2,radius: float,start: float,end: float,count: int,color: Color,width: float,_antialias=true):
	var points=PackedVector2Array()
	for i in range(count+1): points.append(center+Vector2.from_angle(lerpf(start,end,float(i)/count))*radius)
	polyline(points,color,width)
func circle(center: Vector2,radius: float,color: Color):
	var points=PackedVector2Array()
	for i in range(40): points.append(center+Vector2.from_angle(TAU*i/40.0)*radius)
	polygon(points,color)

func note_head(at: Vector2,width: float,color: Color,tail: bool=false):
	var item=take("tail" if tail else "head")
	item.position=at; item.size=Vector2(width,0)
	item.get_node("Head").modulate=color
