"""Enable an emulated audio device for real notification playback checks in CI."""
import os
from pathlib import Path

android_user = Path(os.environ.get("ANDROID_USER_HOME", str(Path.home() / ".android")))
avd_home = Path(os.environ.get("ANDROID_AVD_HOME", str(android_user / "avd")))
config = avd_home / "kin-ci.avd" / "config.ini"
text = config.read_text()
lines = [line for line in text.splitlines() if line.split("=", 1)[0].strip() not in {"hw.audioInput", "hw.audioOutput"}]
config.write_text("\n".join(lines) + "\nhw.audioInput=yes\nhw.audioOutput=yes\n")
