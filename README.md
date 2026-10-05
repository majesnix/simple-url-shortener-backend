## Simple Url Shortener Backend

Frontend: [simple-url-shortener-frontend](https://github.com/majesnix/simple-url-shortener-frontend)

Backend built with Scala, Cats Effect, http4s and Skunk on PostgreSQL.

Deployed version can be found here: [dcl.re](https://dcl.re)

### API

| Method | Path       | Body / response |
|--------|------------|-----------------|
| `POST` | `/`        | `{"url": "https://…", "expiry": "1d"}` → `200 {"short": "AbC123xY"}` |
| `GET`  | `/<short>` | `200 {"url": "https://…"}` or `404` |
| `GET`  | `/health`  | `200 OK` |

`expiry` is optional: `1d`, `1w`, `1m`, `1y`, `unlimited` (default) or `1x`
(one-time link: resolves once, and expires after a month if never opened).

`POST /` answers `400` for invalid URLs (non-http(s), private/loopback hosts,
the service's own host, longer than 2048 chars) or an unknown expiry, `413`
for oversized bodies and `429` (with `Retry-After`) when the rate limit is hit.

### Configuration

All settings live in `src/main/resources/application.conf` and can be
overridden with environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_HOST` / `SERVER_PORT` | `0.0.0.0` / `8080` | Bind address |
| `SERVER_URL` | `localhost` | Public host of the shortener; links to it are rejected |
| `CORS_ORIGINS` | *(empty = any)* | Comma-separated origins allowed to call the API, e.g. `https://dcl.re` |
| `MAX_BODY_BYTES` | `16384` | Request body size limit |
| `RATE_LIMIT_MAX_REQUESTS` | `30` | Link creations per client and window; `0` disables |
| `RATE_LIMIT_WINDOW` | `1 minute` | Rate limit window (HOCON duration) |
| `RATE_LIMIT_FORWARDED_FOR_HOPS` | `0` | Trusted reverse proxies in front of the service. Set to `1` behind nginx/Traefik so clients are told apart by `X-Forwarded-For`; otherwise every request counts against the proxy's address |
| `DB_HOST` / `DB_PORT` / `DB_DATABASE` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `postgres` / `postgres` / `postgres` | PostgreSQL connection |
| `DB_POOL_SIZE` | `32` | Max pooled DB sessions |

Database migrations (Flyway) run automatically on startup. A janitor runs
nightly at 00:00 UTC to delete expired links.

### Development

Requires JDK 25 and sbt (see `.tool-versions`).

```sh
docker compose up -d db   # PostgreSQL on 127.0.0.1:5432
sbt run                   # API on http://localhost:8080
sbt test                  # unit tests
sbt scalafmtCheckAll "scalafixAll --check"
sbt runItTest             # builds the image, starts docker compose, runs it/ tests, tears down
```

### Releasing

The version lives in one place: `ThisBuild / version` in `build.sbt`. It is
used as the Docker tag, and `runItTest` passes it to `docker-compose.yml`.
Bump it in every PR that should produce a new image: pushes to `main` publish
`codingbros/sus-backend:<version>` and `latest`.
