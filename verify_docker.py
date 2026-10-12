import json, urllib.request, urllib.error

BASE = "http://localhost:3000"

def req(method, path, body=None, token=None, timeout=15):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))

def show(label, res):
    status, j = res
    ok = (status == 200 and (j.get("code") == 0 or "data" in j))
    print(f"[{'OK' if ok else 'FAIL'}] {label} -> HTTP {status} code={j.get('code')}")
    return j

# 1. 登录
st, login = req("POST", "/api/auth/login", {"username": "admin", "password": "123456"})
token = login.get("data", {}).get("token", "")
show("登录", (st, login))
print("   token长度:", len(token))

# 2. 套餐（公开）
st, pkg_list = req("GET", "/api/package/list")
show("套餐列表", (st, pkg_list))
data = pkg_list.get("data", {})
items = data.get("list", data if isinstance(data, list) else [])
print("   套餐数量:", len(items))
if items:
    p = items[0]
    print("   示例套餐:", p.get("name"), "/ ¥", p.get("price"), "/ 分类", p.get("category_id"))

st, cat = req("GET", "/api/package/categories")
show("套餐分类", (st, cat))
print("   分类数量:", len(cat.get("data", []) or []))

st, detail = req("GET", "/api/package/1")
show("套餐详情 id=1", (st, detail))
print("   名称:", detail.get("data", {}).get("name"))

# 3. 订单（需 token）
st, bk = req("GET", "/api/booking/list", token=token)
show("订单列表(带token)", (st, bk))
print("   订单总数:", bk.get("data", {}).get("total"))

st, stat = req("GET", "/api/booking/stats/overview", token=token)
show("订单统计", (st, stat))
print("   统计:", stat.get("data"))

# 4. 视频（公开列表）
st, vd = req("GET", "/api/video/list")
show("视频列表", (st, vd))
print("   视频数量:", len(vd.get("data", []) or []))

print("\n===== 全链路验证完成 =====")
