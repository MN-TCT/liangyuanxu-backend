# -*- coding: utf-8 -*-
# 等待 docker 引擎就绪后，立即重建 wedding-backend-v2 容器
import subprocess, sys, io, time
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

def run(args, t=60, cwd=None):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t, cwd=cwd,
                           creationflags=0x08000000)
        return r.returncode, (r.stdout + r.stderr).strip()
    except subprocess.TimeoutExpired:
        return -1, 'timeout'
    except Exception as e:
        return -2, str(e)[:80]

CWD = r"C:\Users\ZLT\Desktop\vben_admin\backend-springboot"
for i in range(1, 121):  # 最多 20 分钟
    code, out = run(['docker', 'ps', '-aq'])
    if code == 0:
        print('[%ds] engine ready, building wedding-backend-v2...' % (i*5))
        rc, ro = run(['docker', 'compose', 'up', '-d'], t=300, cwd=CWD)
        print('compose up rc=%d' % rc)
        print(ro[-1500:] if len(ro) > 1500 else ro)
        if rc == 0:
            print('DONE: wedding-backend-v2 created')
            break
        else:
            print('compose up failed, will retry')
            time.sleep(15)
    else:
        if i % 6 == 0:
            print('[%ds] engine not ready (%s)' % (i*5, out[:60]))
        time.sleep(5)
print('SCRIPT END')
