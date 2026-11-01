extends Node
## A monotonic interpolated audio clock with output latency compensation.
var player=AudioStreamPlayer.new()
var voices=[]
var tap: AudioStream
var hold: AudioStream
var enabled=true
var volume=1.0
var frozen=0.0
var last=0.0
var playing=false
var started_usec=0
var output_latency=0.0
var ended=false
var end_usec=0

func _ready():
	add_child(player); player.volume_db=-2.0
	player.finished.connect(func(): ended=true; end_usec=Time.get_ticks_usec(); frozen=player.stream.get_length())
	tap=load("res://assets/sfx/tap.wav"); hold=load("res://assets/sfx/hold.wav")
	for _i in range(24):
		var voice=AudioStreamPlayer.new(); add_child(voice); voices.append(voice)

func prepare(stream: AudioStream):
	stop(); player.stream=stream; frozen=0.0; last=0.0

func play_from(position: float):
	ended=false; player.stream_paused=false; player.play(maxf(0.0,position)); frozen=maxf(0.0,position); last=frozen; started_usec=Time.get_ticks_usec()
	output_latency=maxf(0.0,AudioServer.get_output_latency()); playing=true

func position() -> float:
	if not playing: return frozen
	if ended: return maxf(last,frozen+(Time.get_ticks_usec()-end_usec)/1000000.0)
	var value=player.get_playback_position()+AudioServer.get_time_since_last_mix()-output_latency
	# Before the first mix, the player's position may still refer to the old stream.
	if Time.get_ticks_usec()-started_usec<50000: value=maxf(frozen,value)
	last=maxf(last,value)
	return last

func pause():
	frozen=position(); player.stream_paused=true; playing=false; stop_hits()

func stop_hits():
	for voice in voices: voice.stop()

func stop():
	player.stop(); playing=false; stop_hits()

func hit(is_hold: bool):
	if not enabled or volume<=0: return
	var available=null
	for voice in voices:
		if not voice.playing: available=voice; break
	# Overlapping hits get independent voices. Steal only when >24 sounds overlap.
	if available==null: available=voices.pop_front(); voices.append(available)
	available.stream=hold if is_hold else tap
	available.volume_db=linear_to_db(maxf(volume,0.00001)); available.play()
