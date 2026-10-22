# Decisiones técnicas, problemas resueltos y guion de entrevista

## Decisiones clave
| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Monolito modular | Microservicios | Un equipo, un despliegue, transacciones simples; los módulos ya están separados por paquete. |
| `Membership` guarda `price` | Leer el precio del plan | El histórico y las cuentas no cambian al subir precios. |
| Estado de membresía **derivado** de fechas | Columna `status` actualizada por un job | Un estado persistido se desfasa; calcularlo no necesita tareas programadas. |
| Bloqueo pesimista de la fila del socio al contratar | Solo comprobar solapamiento | Dos peticiones simultáneas pasarían ambas la comprobación; hay un test de concurrencia. |
| Baja lógica de socios | `DELETE` | Pagos y asistencias deben conservarse. |
| JWT stateless (Resource Server) | Sesión con cookie | API consumida por SPA; sin estado en servidor. Coste: sin revocación inmediata. |
| `PageResponse` propio | Serializar `PageImpl` | El JSON de `PageImpl` no es un contrato estable. |
| SQL directo (`JdbcClient`) en el panel | JPQL | Son informes agregados, no operaciones sobre el modelo. |
| PostgreSQL embebido en tests | H2 / Testcontainers | H2 oculta diferencias reales; Testcontainers necesita Docker, que no funcionaba en la máquina. |
| Importes solo para ADMIN en el panel | Ocultarlos en la UI | La restricción vive en el servidor, no en el cliente. |

## Problemas reales que aparecieron
1. **`lower(bytea)` en PostgreSQL**: un parámetro `null` sin tipo en `(:q is null or lower(...))` rompía la búsqueda.
   Solución: cadena vacía como "sin filtro". Lo detectó un test de integración contra PostgreSQL real (H2 no lo habría visto).
2. **`PageImpl` inestable** en JSON → DTO de paginación propio.
3. **Condición de carrera** al contratar → bloqueo de fila + test con dos hilos (uno 201, otro 409).
4. **Un campo, dos mensajes de validación** (`@NotBlank` + `@Pattern`) → un mensaje por campo.
5. **Versión de Spring Boot**: Initializr devolvía `4.1.1.RELEASE`, inexistente en Maven Central; correcto: `4.1.1`.
6. `npm install` fallaba con un error interno de npm (`edgesOut`); se resolvió con `--legacy-peer-deps`.

## Qué no está verificado (honestidad)
- `docker compose up --build`: Docker Desktop no arrancaba en la máquina de desarrollo. Los Dockerfiles y compose están escritos pero **sin probar**.
- Las pantallas se compilaron y la API se probó por HTTP a través del proxy de Angular, pero no se inspeccionaron visualmente en un navegador (la extensión de Chrome no estaba conectada).

## Cómo presentarlo (5 minutos)
1. **Problema** (30 s): recepción con Excel; entra gente sin pagar, nadie sabe quién se va.
2. **Demo** (2 min): Check-in verde/rojo → ficha con saldo → panel con radar → WhatsApp prellenado.
3. **Una decisión técnica** (1,5 min): la carrera al contratar membresías y cómo se probó.
4. **Calidad** (1 min): 27 tests contra PostgreSQL real, roles en servidor, qué *no* está hecho.
5. **Siguiente paso** (30 s): refresco de tokens, gestión de usuarios, reservas de clases.

## Preguntas probables
**¿Por qué un monolito?** Un dominio acotado y un equipo pequeño; microservicios añadirían red, despliegues y consistencia
distribuida sin beneficio. Los paquetes por dominio permiten extraer un módulo si hiciera falta.

**¿Cómo evitas membresías solapadas con peticiones simultáneas?** Se bloquea la fila del socio (`SELECT … FOR UPDATE`)
dentro de la transacción de contratación; la segunda petición espera y, al comprobar el solapamiento, recibe 409.

**¿Por qué guardas el precio en la membresía?** Para que cambiar el precio del plan no reescriba el pasado: deuda,
ingresos y recibos históricos siguen siendo correctos.

**¿Cómo manejas el dinero?** `NUMERIC(10,2)` en BD y `BigDecimal` en Java, nunca `double`. Los pagos son un registro de
cobros externos; no se procesan ni custodian fondos.

**¿Qué pasa si se cae el servidor a mitad de "contratar y cobrar"?** Ambas operaciones están en una transacción: o se
guardan las dos o ninguna (hay un test que fuerza el fallo del pago y comprueba que no queda membresía huérfana).

**¿Cómo protegiste el login?** BCrypt, mensaje idéntico para usuario inexistente y contraseña errónea, y una comparación de
hash de relleno para igualar tiempos. Falta: límite de intentos, que es la primera mejora de seguridad pendiente.

**¿Por qué el token en `localStorage`?** Compromiso de usabilidad (recepción recarga a menudo). Riesgo: XSS; se mitiga con
el escapado de Angular, CSP en nginx y caducidad corta. La alternativa es cookie `HttpOnly` + CSRF, o refresh tokens.

**¿Cómo se prueba?** Tests de integración con MockMvc sobre un PostgreSQL real embebido y migraciones Flyway, más tests
unitarios de interceptor y utilidades en Angular.

**¿Cómo escalaría?** API stateless → réplicas tras un balanceador; índices ya definidos para búsqueda y vencimientos;
el panel haría agregaciones en BD y, si crece, una vista materializada o caché.

## Textos
**Descripción de GitHub (≤ 350 car.)**
> GymFlow: gestión de gimnasios con Angular 21 y Spring Boot 4. Socios, membresías sin solapamiento (con control de
> concurrencia), cobros parciales, check-in con verde/rojo y un radar de retención con avisos por WhatsApp. 27 tests
> de integración contra PostgreSQL real, JWT con roles y Docker Compose.

**Publicación de LinkedIn**
> He construido GymFlow, una aplicación para recepción y administración de gimnasios.
>
> Lo que más me interesó resolver: que dos recepcionistas no puedan contratar membresías solapadas a la vez (bloqueo de fila
> y un test con dos hilos), que cambiar un precio no reescriba el histórico, y un radar que detecta a los socios que
> dejan de venir antes de que se den de baja.
>
> Stack: Angular 21, Spring Boot 4, PostgreSQL, Flyway, JWT, Docker. Tests contra PostgreSQL real, no contra un simulador.
> En el README explico también lo que todavía no está hecho. Código: [enlace]
