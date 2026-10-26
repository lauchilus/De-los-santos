"""Comprueba el contenedor local de CI; nunca recibe una URL de produccion."""
import base64
import json
import os
import time
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:10000'
def get(path, headers=None):
    req = urllib.request.Request(BASE + path, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read(), r.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers

for _ in range(90):
    try:
        if get('/actuator/health')[0] == 200:
            break
    except (OSError, urllib.error.URLError):
        pass
    time.sleep(2)
else:
    raise SystemExit('La aplicacion no alcanzo estado saludable')

status, page, _ = get('/')
assert status == 200 and b'<html' in page.lower(), 'No se sirve el frontend'
assert get('/sw.js')[0] == 200, 'Falta el service worker'
status, _, headers = get('/api/v1/auth/me')
assert status == 401 and 'WWW-Authenticate' not in headers, 'API anonima incorrecta'
auth = base64.b64encode(('admin:' + os.environ['APP_ADMIN_PASSWORD']).encode()).decode()
status, data, _ = get('/api/v1/auth/me', {'Authorization': 'Basic ' + auth})
assert status == 200 and json.loads(data)['username'] == 'admin', 'Login fallido'
status, data, _ = get('/api/v1/csrf', {'Authorization': 'Basic ' + auth})
assert status == 200 and json.loads(data).get('token'), 'CSRF no disponible'
print('Imagen saludable: frontend, API, autenticacion y CSRF correctos')
