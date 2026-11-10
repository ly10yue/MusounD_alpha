extends Node
## Android alpha releases. The local APK signing key also authenticates release metadata.
const BASE = "http://49.235.183.159/musound/android/"
const APK = "user://musound-update.apk"
const PART = "user://musound-update.part"
const MAX_BYTES = 256 * 1024 * 1024
const PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArXYCIg7No/PahkPA7Yw8
LA74dne0hGh42Jy1I9UfrpEdF0Q1/Sgz0ash7ZtMUbnVUiy6bOzujqckGKjKyb+e
HuDoKcXzkuQhndCisoeBxjyEvpwMgTZ9kSlk0hU2ZBzW5Mit8NQI/JqzgpIS4upo
KhOJni6kxe6HvYaWh0aV9H4I+2yfg2x6md4OEos7Ef/dG3kUjLbaRYJO4Y8XmjeS
Sn42xPsklnp5042QLgC8YxPe1cuFQ7HwDc2UymKo8WIepZhExZwm8mQnjSs2zftH
1g4YdhNTFrNuCN+1NCcIPH/qL7ArJ8g1HDSWEabEFZaYLXhOYJceuWs3osAs3voi
mwIDAQAB
-----END PUBLIC KEY-----
"""

@onready var button: Button = $"../UI/Title/Update"
var request: HTTPRequest
var release: Dictionary
var hashing: Thread
var checking_cache := false
var downloading := false
var ready_to_install := false

func _ready():
	set_process(false)
	if OS.get_name() != "Android":
		return
	$"../UI/Title/Offline".hide()
	button.show()
	button.pressed.connect(on_pressed)
	request = HTTPRequest.new()
	request.use_threads = true
	request.accept_gzip = false
	request.max_redirects = 0
	add_child(request)
	request.request_completed.connect(on_response)
	check()

func status(label: String, actionable := false):
	button.text = label
	button.disabled = not actionable

func on_pressed():
	if ready_to_install:
		open_installer()
	else:
		check()

func check():
	status("正在检查更新…")
	release = {}
	request.download_file = ""
	request.body_size_limit = 8192
	request.timeout = 8
	if request.request(BASE + "latest.json", ["Cache-Control: no-cache"]) != OK:
		status("连接失败 · 重试", true)

static func read_manifest(body: PackedByteArray) -> Dictionary:
	if body.size() > 8192:
		return {}
	var envelope = JSON.parse_string(body.get_string_from_utf8())
	if not envelope is Dictionary or not envelope.get("payload") is String or not envelope.get("signature") is String:
		return {}
	var payload: String = envelope.payload
	var key := CryptoKey.new()
	if key.load_from_string(PUBLIC_KEY, true) != OK or not Crypto.new().verify(HashingContext.HASH_SHA256, payload.sha256_buffer(), Marshalls.base64_to_raw(envelope.signature), key):
		return {}
	var data = JSON.parse_string(payload)
	if not data is Dictionary or data.get("package") != "studio.interval":
		return {}
	for field in ["code", "size"]:
		if typeof(data.get(field)) not in [TYPE_INT, TYPE_FLOAT] or data[field] != floor(data[field]) or data[field] < 1:
			return {}
	if data.size > MAX_BYTES or data.code > 2100000000 or not data.get("version") is String or not data.get("sha256") is String:
		return {}
	if data.sha256.length() != 64 or not data.sha256.is_valid_hex_number(false):
		return {}
	return data

func on_response(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray):
	if downloading:
		downloading = false
		set_process(false)
		if result != HTTPRequest.RESULT_SUCCESS or code != 200:
			discard(PART)
			status("下载失败 · 重试", true)
			return
		verify_file(PART)
		return
	if result != HTTPRequest.RESULT_SUCCESS or code != 200:
		status("无法检查更新 · 重试", true)
		return
	release = read_manifest(body)
	if release.is_empty():
		status("更新清单无效 · 重试", true)
		return
	if int(release.code) <= int(ProjectSettings.get_setting("application/config/android_version_code")):
		discard(APK)
		discard(PART)
		status("已是最新版 · 再次检查", true)
	elif FileAccess.file_exists(APK):
		verify_file(APK)
	else:
		download()

func discard(path: String):
	if FileAccess.file_exists(path):
		DirAccess.remove_absolute(path)

func download():
	status("正在下载更新…")
	discard(PART)
	request.download_file = PART
	request.body_size_limit = int(release.size)
	request.timeout = 1200
	downloading = true
	if request.request(BASE + "MusounD-%d.apk" % int(release.code)) != OK:
		downloading = false
		status("下载失败 · 重试", true)
	else:
		set_process(true)

func _process(_delta):
	if downloading and button.is_visible_in_tree():
		status("正在下载更新 %d%%" % clampi(int(request.get_downloaded_bytes() * 100.0 / release.size), 0, 100))

func verify_file(path: String):
	status("正在校验安装包…")
	checking_cache = path == APK
	hashing = Thread.new()
	var expected_size := int(release.size)
	var expected_hash := str(release.sha256)
	var err := hashing.start(func():
		var file := FileAccess.open(path, FileAccess.READ)
		var correct := file != null and file.get_length() == expected_size
		file = null
		var valid := correct and FileAccess.get_sha256(path) == expected_hash
		verified.call_deferred(valid))
	if err != OK:
		hashing = null
		status("无法校验安装包 · 重试", true)

func verified(valid: bool):
	hashing.wait_to_finish()
	hashing = null
	if not valid:
		if checking_cache:
			discard(APK)
			download()
		else:
			discard(PART)
			status("安装包校验失败 · 重试", true)
		return
	if not checking_cache and DirAccess.rename_absolute(PART, APK) != OK:
		status("保存安装包失败 · 重试", true)
		return
	ready_to_install = true
	status("安装更新  " + release.version, true)

func open_installer():
	var runtime = Engine.get_singleton("AndroidRuntime")
	var activity = runtime.getActivity()
	var intent_class = JavaClassWrapper.wrap("android.content.Intent")
	var provider = JavaClassWrapper.wrap("androidx.core.content.FileProvider")
	var file_class = JavaClassWrapper.wrap("java.io.File")
	var file = file_class.File(ProjectSettings.globalize_path(APK))
	var uri = provider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file)
	if uri == null:
		status("无法打开安装包 · 重试", true)
		return
	var intent = intent_class.Intent("android.intent.action.VIEW")
	intent.setDataAndType(uri, "application/vnd.android.package-archive")
	intent.addFlags(1) # FLAG_GRANT_READ_URI_PERMISSION
	activity.startActivity(intent)
	if JavaClassWrapper.get_exception() != null:
		status("无法打开安装器 · 重试", true)

func _exit_tree():
	if hashing != null and hashing.is_started():
		hashing.wait_to_finish()
