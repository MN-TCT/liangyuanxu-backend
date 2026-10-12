# -*- coding: utf-8 -*-
"""婚庆视频上传接口测试（小文件 + 直连 video 服务，定位 multipart 问题）"""
import io
import json
import urllib.request
import uuid

BASE = "http://localhost:3004"  # 直连 video 服务


def login():
    body = json.dumps({"username": "admin", "password": "123456"}).encode()
    req = urllib.request.Request(
        "http://localhost:3000/api/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read())["data"]["token"]


def multipart(fields, file_bytes, filename="test.mp4", ctype="video/mp4"):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(("--" + boundary).encode())
        parts.append('Content-Disposition: form-data; name="{}"\r\n\r\n'.format(k).encode())
        parts.append(v.encode())
        parts.append(b"\r\n")
    parts.append(("--" + boundary).encode())
    parts.append(
        'Content-Disposition: form-data; name="file"; filename="{}"\r\nContent-Type: {}\r\n\r\n'.format(
            filename, ctype
        ).encode()
    )
    parts.append(file_bytes)
    parts.append(b"\r\n--" + boundary.encode() + b"--\r\n")
    return boundary, b"".join(parts)


def main():
    token = login()
    small = b"\x00\x00\x00\x18ftypmp42" + b"\x00" * 2048
    boundary, body = multipart(
        {"title": "小文件测试", "durationSeconds": "5", "description": "中文描述"},
        small,
    )
    req = urllib.request.Request(
        BASE + "/api/video/upload",
        data=body,
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "multipart/form-data; boundary=" + boundary,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            print("STATUS:", r.status)
            print("RESP:", json.dumps(json.loads(r.read()), ensure_ascii=False))
    except urllib.error.HTTPError as e:
        print("HTTP ERROR:", e.code)
        print("BODY:", e.read().decode("utf-8", "replace")[:500])
    except Exception as e:
        print("EXC:", type(e).__name__, e)


if __name__ == "__main__":
    main()
