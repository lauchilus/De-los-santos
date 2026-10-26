# Agua de los Santos: Render + Neon

Este paquete se aplica SOLO al repositorio backend `lauchilus/De-los-santos`, rama `main`.
No reemplaza todo el backend: copiá su contenido sobre la raíz donde está `pom.xml`.
El frontend se compila desde `lauchilus/agua-frontend`, revisión exacta `a58a5188e448358675e19bd5b56a46e943d72dbf`.
Ambos repositorios fueron consultados directamente. No se subieron cambios ni se creó infraestructura en tus cuentas.

## 1. Subir la preparación

Copiá Dockerfile, .dockerignore, render.yaml, esta guía y las carpetas src del paquete sobre tu backend. Conservá el resto de tus archivos.
Desde PowerShell, en la raíz del backend:

```powershell
.\mvnw.cmd -Dtest=PublicFrontendSecurityTest test
```

```powershell
git add Dockerfile .dockerignore render.yaml DESPLIEGUE-RENDER.md src/main/resources/application-prod.yaml src/main/java/com/agua/backend/seguridad/SecurityConfig.java src/test/java/com/agua/backend/seguridad/PublicFrontendSecurityTest.java
```

```powershell
git diff --cached --stat
```

```powershell
git commit -m "Prepara despliegue Render con frontend integrado y Neon"
```

```powershell
git push origin main
```

No hace falta cambiar el frontend. Tampoco se ejecuta Vite preview en producción ni se copian .env/.certs. Render termina HTTPS y Spring sirve la interfaz y /api en el mismo dominio.

## 2. Crear PostgreSQL en Neon

En Neon: New Project, nombre `agua-de-los-santos`, plan Free. Elegí PostgreSQL 18 si está disponible, y AWS US East (Virginia) para acercarlo al servicio Render. Podés usar la base `neondb` creada automáticamente.

Abrí Connect y desactivá Connection pooling: para esta primera versión usaremos conexión directa con un pool pequeño en Spring, también para las migraciones Flyway. Anotá privadamente host, database, user y password.

IMPORTANTE: elegí antes de desplegar:

- Base nueva de pruebas: no importes nada. Flyway crea tablas/productos y la aplicación crea admin al iniciar.
- Conservar tu base local: primero respaldá y restaurá la base completa en una base Neon VACÍA, incluyendo flyway_schema_history. No inicies la aplicación antes de restaurar. No mezcles un restore completo con una base ya inicializada. Los usuarios restaurados conservan sus contraseñas; APP_ADMIN_PASSWORD no modifica un admin existente.

No hay copia automática de tu PostgreSQL local. No ejecutes pruebas destructivas contra Neon. Para mover datos reales, coordiná un corte, sincronizá los pendientes y conservá el respaldo local.

## 3. Crear el servicio en Render

Después del push, Render > New > Blueprint, conectá GitHub y elegí `lauchilus/De-los-santos`, rama main. Usá `render.yaml` de la raíz. Revisá que se cree UN Web Service con plan Free y NINGUNA base de datos Render.

El archivo fija: Docker, región Virginia, health check `/actuator/health/liveness` y perfil prod. El chequeo liveness no consulta PostgreSQL en cada petición, para no mantener Neon despierto solo por monitoreo.

Completá estas variables privadas en Render:

| Variable | Valor |
| --- | --- |
| DB_URL | `jdbc:postgresql://HOST-NEON:5432/neondb?sslmode=require&channelBinding=require` |
| DB_USERNAME | El usuario que muestra Neon |
| DB_PASSWORD | La contraseña que muestra Neon |
| APP_ADMIN_PASSWORD | Contraseña nueva, única, mínimo 12 caracteres y máximo 72 bytes UTF-8; crea admin SOLO si no existe ningún administrador |

Reemplazá HOST-NEON y neondb con tus valores. DB_URL debe empezar con jdbc:postgresql://, no con postgresql://. No incluyas el usuario o contraseña dentro de la URL. No pegues la cadena `psql '...'` completa.

Si el asistente Blueprint no ofrece el plan gratis, cancelá antes de crear y usá New > Web Service: mismo repositorio/main, lenguaje Docker, Root Directory vacío, Dockerfile ./Dockerfile, Free, Virginia. Cargá las cuatro variables y SPRING_PROFILES_ACTIVE=prod; en Advanced poné el mismo health check.

Render obtiene el frontend desde su repositorio público durante el build. Esto requiere que dicho repositorio siga accesible; si se hace privado habrá que cambiar el mecanismo, sin insertar tokens en Dockerfile.

## 4. Primer ingreso

Esperá que el build y el arranque finalicen correctamente. Abrí la dirección HTTPS que Render asigne. En base nueva: usuario admin y contraseña APP_ADMIN_PASSWORD. En base restaurada: tus credenciales anteriores.

Verificaciones:

1. `/` muestra el login sin pedir Basic Auth en una ventana del navegador.
2. `/actuator/health/liveness` muestra UP; `/actuator/health` confirma además acceso a la base.
3. Login admin, clientes, productos, reportes; repartidor sin acceso a administración.
4. Con datos de prueba, entrega y cobro; desconectar/red/reintento sin duplicar.
5. Recargar una vez online en el celular antes de probar offline, para instalar el service worker.

Sincronizá lo pendiente en la dirección local ANTES de pasarte a Render. IndexedDB y las colas son por origen: no se trasladan a la nueva dirección. No borres el almacenamiento local hasta verificar que quedó todo confirmado.

## 5. Actualizaciones

Cambios del backend: commit + push a main y despliegue en Render.
Cambios del frontend: subí el commit a agua-frontend y copiá su SHA completo en FRONTEND_REF del Dockerfile del backend; luego commit + push del backend. Un push al frontend por sí solo NO publica otra UI. Así cada despliegue tiene una versión reproducible.

Para volver a una versión, conservá los commits anteriores. No reviertas migraciones de base automáticamente: primero comprobá compatibilidad.

## 6. Límites y diagnóstico

Render Free puede suspender el servicio por inactividad; al volver, el arranque puede demorar aproximadamente un minuto. El frontend conserva la cola offline, pero el primer login necesita conexión. Neon Free tiene cuotas: monitoreá consumo en ambos paneles. No configures pings para evitar la suspensión.

La JVM está limitada para una instancia de 512 MB: heap 256 MB, metaspace 128 MB, pocos hilos y pool de 4 conexiones. Esto necesita observarse con el tráfico real, especialmente importaciones grandes y Excel. Si Render informa OutOfMemory o reinicios, revisá métricas y aumentá memoria/plan; no aumentes el heap por encima de la capacidad del servicio.

- Error DB_URL: revisar prefijo JDBC, host directo, base y SSL.
- Password authentication failed: revisar usuario/contraseña en Render.
- Falta APP_ADMIN_PASSWORD: completar para una base nueva.
- Fallo Flyway: no borrar flyway_schema_history ni poner baseline automáticamente; revisar log.
- Build no obtiene frontend: comprobar visibilidad y SHA.
- Dormido: esperar arranque y reintentar; no regenerar identificadores de operaciones pendientes.

La configuración local sigue usando application.yaml; prod solo se activa en Docker/Render. Los certificados LAN y el PostgreSQL local permanecen intactos.

Fuentes consultadas:
- https://render.com/docs/docker
- https://render.com/docs/blueprint-spec
- https://render.com/docs/free
- https://neon.com/docs/guides/java

## Verificación realizada

Frontend del commit indicado: compilación correcta. Se agregaron 5 pruebas de seguridad (shell público, API autenticada, rol admin, CSRF y rutas no expuestas). No fue posible ejecutarlas aquí: el entorno no pudo conectar con Maven Central para descargar Spring Boot. Ejecutá el comando del paso 1 en tu PC antes del commit. Tampoco se ejecutó el contenedor Docker en este entorno; el primer build de Render verificará la imagen completa.
