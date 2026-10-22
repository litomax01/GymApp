# GymFlow

Gestión de gimnasios para recepción y administración: socios, membresías, cobros, control de acceso y un
**radar de retención** que avisa de los socios que están a punto de abandonar.

> Registra cobros hechos fuera del sistema; **no procesa pagos ni custodia dinero**.

## El problema
Muchos gimnasios pequeños llevan las cuotas en Excel: se cuela gente con la cuota vencida, los cobros se olvidan y
nadie sabe quién está dejando de venir. GymFlow resuelve la operación diaria en una sola pantalla.

## Funcionalidades
| Área | Qué hace |
|---|---|
| Acceso | Login JWT con roles **ADMIN** y **RECEPTION**. |
| Socios | Alta, edición, búsqueda parcial paginada, baja lógica (conserva historial). |
| Planes y membresías | Catálogo de planes; contratar/renovar encadena periodos sin solapar; **precio congelado** por membresía. |
| Pagos | Cobro total o parcial, saldo pendiente, rechazo de sobrepagos; contratación + cobro en una transacción. |
| Check-in | Valida la cuota en el momento; muestra verde/rojo con motivo; los accesos denegados también se registran. |
| Panel | Socios al día, vencimientos próximos, entradas de hoy, ingresos del mes (solo ADMIN). |
| **Radar de retención** | Socios con cuota vigente sin venir ≥ 14 días o a punto de vencer, con mensaje de WhatsApp prellenado. |

## Stack
Angular 21 (standalone, signals, formularios reactivos, carga diferida) · Spring Boot 4.1 / Java 21 ·
PostgreSQL 16 · Flyway · Spring Security (JWT) · JUnit 5 + MockMvc contra PostgreSQL real · Docker Compose · nginx.

## Arquitectura
```
Navegador ──► nginx ─┬─ /          Angular (SPA)
                     └─ /api  ───► Spring Boot (monolito modular) ──► PostgreSQL
backend/com.gymflow: common · security · auth · user · member · plan · membership · checkin · dashboard · demo
frontend/src/app:    core (api, auth, errores) · features (login, shell, dashboard, checkin, members, plans)
```
Decisiones y su porqué: [`docs/02-decisiones-y-entrevista.md`](docs/02-decisiones-y-entrevista.md).
Análisis de producto y modelo de datos: [`docs/01-analisis-y-plan.md`](docs/01-analisis-y-plan.md).

## Ejecutar con Docker
```bash
cp .env.example .env        # define DB_PASSWORD y JWT_SECRET (openssl rand -base64 48)
SPRING_PROFILES_ACTIVE=demo docker compose up --build    # sin "demo" arranca vacío y sin usuarios
# http://localhost:8088   admin@gymflow.test / recepcion@gymflow.test  ·  Admin123!
```
Sin el perfil `demo` no existe ningún usuario: crea el primero directamente en la base de datos
(hash BCrypt en `app_users`). Una gestión de usuarios desde la interfaz está pendiente.

## Ejecutar en local sin Docker ni PostgreSQL
Requiere JDK 21+ y Node 22+.
```bash
cd backend  && ./mvnw spring-boot:test-run      # API en :8081 con PostgreSQL embebido y datos demo
cd frontend && npm install --legacy-peer-deps && npx ng serve   # http://localhost:4200 (proxy /api → :8081)
```

## Tests
```bash
cd backend  && ./mvnw test           # 27 tests de integración (PostgreSQL embebido real)
cd frontend && npx ng test --watch=false   # 11 tests unitarios
```
Cubren: autenticación, autorización por rol, validación, duplicados, solapamiento de membresías (incluida una
carrera concurrente), sobrepagos y reversión transaccional, check-in permitido/denegado, y las cifras del panel.

## Seguridad
BCrypt · JWT HS256 con secreto obligatorio (≥ 32 caracteres) · respuesta de login idéntica para email inexistente o
contraseña errónea · roles verificados en servidor (`@PreAuthorize`) · validación en servidor y cliente ·
CORS restringido · cabeceras CSP/X-Frame-Options en nginx · errores sin trazas internas · el panel oculta importes a RECEPTION.

## Limitaciones conocidas
- Sin límite de intentos de login ni refresco/revocación de tokens (JWT de 8 h en `localStorage`).
- Sin gestión de usuarios en la interfaz; sin exportaciones; sin multi-sede.
- Imágenes Docker y pruebas visuales en navegador **no verificadas en el entorno de desarrollo** (ver docs).
- Datos personales (documento, teléfono): aplica la normativa de protección de datos de tu país.

## Capturas sugeridas para el portafolio
Panel con radar · Check-in en verde y en rojo · Ficha de socio con membresías y saldo · Planes.
