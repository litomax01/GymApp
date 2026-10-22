# GymFlow — Análisis, arquitectura y plan

## A. Problema
Los gimnasios pequeños y medianos (1–3 sedes, 100–1500 socios) controlan membresías en Excel, cuadernos o
software de escritorio caro. Consecuencias: socios que entran con la cuota vencida sin que nadie lo note, cobros
olvidados, ningún dato de retención, y recepcionistas que pierden minutos buscando a cada persona.

**Usuarios**
| Rol | Necesidad principal |
|---|---|
| Recepción | Registrar la entrada en segundos y saber si la cuota está vigente; cobrar y renovar. |
| Administrador/dueño | Ver ingresos, vencimientos próximos y qué socios se están perdiendo. |

**Alternativas**: Mindbody/Glofox (potentes, caros y pensados para cadenas), Excel (sin control), apps locales
(sin web ni móvil). **Propuesta**: ligero, instalable en minutos, centrado en la operación diaria de recepción.

## B. MVP (6 funcionalidades)
1. **Autenticación y roles** (ADMIN, RECEPTION) con JWT.
2. **Socios**: alta, edición, búsqueda, baja lógica.
3. **Planes y membresías**: catálogo de planes; contratar/renovar calcula vigencia (sin solapar periodos).
4. **Pagos**: registro manual (efectivo/tarjeta/transferencia) asociado a la membresía; estado de cuenta.
   *No se procesan pagos ni se custodia dinero; sólo se registra el cobro hecho fuera.*
5. **Check-in de acceso**: busca al socio, valida membresía vigente, registra ALLOWED/DENIED con motivo.
6. **Dashboard**: socios activos, vencen en 7 días, ingresos del mes, asistencia de hoy, **socios en riesgo**.

**Criterios de aceptación clave**
- Login incorrecto → 401 genérico; endpoints sin token → 401; rol insuficiente → 403.
- Documento de socio único (409 si se repite). Baja lógica conserva historial.
- No puede existir una membresía que solape otra activa del mismo socio.
- Check-in con membresía vencida/inexistente → DENIED con motivo, queda registrado; con vigente → ALLOWED.
- Un pago ≤ 0 se rechaza; pago y membresía se crean en una sola transacción.
- Dashboard coincide con los datos (verificado por tests de integración).

**Fuera del MVP** (mejoras futuras): pasarela de pagos (Stripe/Mercado Pago en sandbox), QR/PWA para socios,
clases y reservas, multi-sede, notificaciones por email/WhatsApp, exportación CSV/PDF, auditoría completa.

## C. Diferenciadores
1. **Radar de retención**: lista accionable de socios en riesgo (sin asistir ≥ 14 días con membresía activa, o que
   vencen en ≤ 7 días) con acción de contacto en un clic (`wa.me` con mensaje prellenado).
2. **Check-in en un paso** con semáforo (verde/rojo) y motivo claro, pensado para uso en mostrador.
3. **Precio congelado por membresía**: cambiar el precio de un plan no altera el histórico (decisión de modelo).
4. Calidad de ingeniería visible: tests de integración contra PostgreSQL real, migraciones Flyway, API con contrato
   de error uniforme, seguridad stateless, Docker Compose.

## D. Arquitectura y tecnologías
Monolito modular por dominio (no microservicios: un equipo, un despliegue, transacciones simples).

| Capa | Elección | Motivo |
|---|---|---|
| Frontend | Angular 21 (standalone, signals) + TypeScript estricto | Stack pedido; CLI 21 instalada |
| Backend | Spring Boot 4.1.1, Java 21 (release; corre en 21+) | Boot 4 es la línea vigente de Initializr; Java 21 LTS |
| BD | PostgreSQL + Flyway | Integridad relacional, migraciones versionadas |
| Seguridad | Spring Security + Resource Server (JWT HS256), BCrypt | Sin librerías extra; stateless |
| Tests | JUnit 5, MockMvc, **PostgreSQL embebido (zonky)** | Docker no disponible en esta máquina; PG real sin Docker |
| Despliegue | Docker Compose (db + api + web) | Entrega 5 |

Paquetes backend (`com.gymflow`): `common`, `security`, `auth`, `user`, `member`, `plan`, `membership`,
`payment`, `checkin`, `dashboard`. Cada módulo: entidad, repositorio, servicio (transacciones), controlador, DTOs.
Los controladores nunca exponen entidades; las reglas de negocio viven en los servicios.

## E. Modelo de datos
Implementado en `backend/src/main/resources/db/migration/V1__schema_inicial.sql`.

```
app_users 1───* payments *───1 memberships *───1 members 1───* check_ins
                                      *───1 plans
```
- `members.document_number` único; índice por apellido/nombre (búsqueda).
- `memberships(price, start_date, end_date, status)`; índice parcial por `end_date` para consultas de vencimiento.
- `payments.amount > 0`, `method ∈ {CASH, CARD, TRANSFER}`; `check_ins.result ∈ {ALLOWED, DENIED}`.
- Restricciones CHECK y FKs en la BD además de la validación en la API.

## F. Pantallas
1. **Login**. 2. **Dashboard** (tarjetas KPI, radar de retención, vencimientos). 3. **Check-in** (buscador grande,
resultado verde/rojo a pantalla completa). 4. **Socios** (tabla con búsqueda y paginación, ficha con
membresías, pagos y asistencias). 5. **Planes**. 6. **Nueva membresía/cobro** (diálogo).
Todas con estados de carga, error y vacío; navegación por teclado y contraste AA; responsive (recepción usa tablet).

## G. Entregas
| # | Contenido | Estado |
|---|---|---|
| 1 | Backend base: esquema completo, JWT, roles, socios CRUD/búsqueda, errores uniformes, tests | **Hecha** |
| 2 | Planes, membresías (sin solapamiento) y pagos transaccionales + tests | **Hecha** |
| 3 | Check-in, dashboard y radar de retención + datos demo | **Hecha** |
| 4 | Frontend Angular: login, layout, socios, planes, membresías, check-in, dashboard | **Hecha** |
| 5 | Docker Compose, README, guion de entrevista y textos (Docker sin probar) | **Hecha** |

## Cómo probar la Entrega 1
```
cd backend
./mvnw test                 # 27 tests de integración contra PostgreSQL embebido
./mvnw spring-boot:test-run # API en http://localhost:8081 (perfil demo)
```
Usuarios demo: `admin@gymflow.test` / `recepcion@gymflow.test`, contraseña `Admin123!` (sólo perfil `demo`).
En Windows usa JDK 21+ (`JAVA_HOME`).

## Riesgos y limitaciones conocidas
- Roles: hoy cualquier usuario autenticado accede a socios; la restricción por rol se aplica en Entrega 2–3.
- Sin rate-limit en login (pendiente: bloqueo por intentos).
- Token JWT sin refresh ni revocación (TTL 8 h por defecto).
- Datos personales (documento, teléfono): sujetos a GDPR/LOPD según país; no se simula cumplimiento legal.
