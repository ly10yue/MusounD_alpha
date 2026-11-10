"""Validate a release APK and stage the two public files served by nginx."""
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

ROOT = Path(__file__).resolve().parents[1]


def main():
    release = json.loads((ROOT / "release.json").read_text())
    version, code = release["appVersion"], release["androidVersionCode"]
    apk = ROOT / "dist" / f"MusounD-{version}.apk"
    out = ROOT / "dist" / "android-updates"
    config = (ROOT / "godot/project.godot").read_text(encoding="utf-8")
    if f"config/android_version_code={code}" not in config.splitlines():
        raise SystemExit("Android updater versionCode differs from release.json")
    sdk = Path(os.environ.get("ANDROID_HOME") or Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    build_tools = max((p for p in (sdk / "build-tools").iterdir() if re.fullmatch(r"\d+(\.\d+)*", p.name)),
                      key=lambda p: tuple(map(int, p.name.split("."))))
    manifest = subprocess.check_output([build_tools / "aapt.exe", "dump", "badging", apk]).decode("utf-8")
    expected = f"package: name='studio.interval' versionCode='{code}' versionName='{version}'"
    if not manifest.startswith(expected) or "application-debuggable" in manifest:
        raise SystemExit("Expected a release APK with the package and version from release.json")
    for perm in ["INTERNET", "REQUEST_INSTALL_PACKAGES"]:
        if f"name='android.permission.{perm}'" not in manifest:
            raise SystemExit("Missing permission: " + perm)
    key, cert, _ = pkcs12.load_key_and_certificates((ROOT / "build/debug.jks").read_bytes(), b"android")
    java = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Android/Android Studio/jbr")) / "bin/java.exe"
    signer = subprocess.check_output([java, "-jar", build_tools / "lib/apksigner.jar", "verify",
                                      "--print-certs", apk]).decode("utf-8")
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", signer)
    if {digest.lower() for digest in digests} != {cert.fingerprint(hashes.SHA256()).hex()}:
        raise SystemExit("APK does not have the existing alpha release signature")
    public = cert.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
    if public.decode().strip() not in (ROOT / "godot/services/android_update.gd").read_text(encoding="utf-8"):
        raise SystemExit("Signing key differs from the public key embedded in the updater")
    with apk.open("rb") as file:
        digest = hashlib.file_digest(file, "sha256").hexdigest()
    payload = json.dumps(dict(package="studio.interval", version=version, code=code,
                              size=apk.stat().st_size, sha256=digest), separators=(",", ":"))
    signature = key.sign(payload.encode(), padding.PKCS1v15(), hashes.SHA256())
    out.mkdir(exist_ok=True)
    shutil.copyfile(apk, out / f"MusounD-{code}.apk")
    (out / "latest.json").write_text(json.dumps(dict(payload=payload, signature=base64.b64encode(signature).decode())),
                                     encoding="utf-8")
    print(f"{out}  {digest}")


if __name__ == "__main__":
    main()
