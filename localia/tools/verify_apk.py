"""Verify APK container and 64-bit ELF load alignment. Android build tools verify signature/ZIP alignment separately."""
import hashlib
import pathlib
import struct
import sys
import zipfile

apk = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, "CRC failure"
    names = archive.namelist()
    assert "AndroidManifest.xml" in names
    assert any(n.startswith("classes") and n.endswith(".dex") for n in names)
    for name in names:
        if name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) and name.endswith(".so"):
            data = archive.read(name)
            assert data[:5] == b"\x7fELF\x02", name
            assert data[5] == 1, "Expected little-endian ELF"
            offset = struct.unpack_from("<Q", data, 32)[0]
            size, count = struct.unpack_from("<HH", data, 54)
            for index in range(count):
                header = offset + index * size
                if struct.unpack_from("<I", data, header)[0] == 1:
                    align = struct.unpack_from("<Q", data, header + 48)[0]
                    assert align >= 16384, f"{name}: PT_LOAD alignment {align} < 16384"
            print(f"16 KB ELF OK: {name}")
print(f"APK ZIP CRC OK; size {apk.stat().st_size} bytes")
print(f"SHA256 {hashlib.sha256(apk.read_bytes()).hexdigest()}")
