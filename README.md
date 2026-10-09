# HexagonalBoot

HexagonalBoot is a **hexagonal architecture framework** for building business APIs. Your domain lives in plugins; the framework does not.

It is an evolution of **[Picollo](https://github.com/DinizR/picollo)**, also on GitHub. Picollo used **OSGi** as the plugin runtime. HexagonalBoot does **not**: plugins are ordinary JARs loaded from a runtime home, and the platform is organized as **ports and adapters** (hexagonal architecture) instead of OSGi bundles and services.

This repository is **one host**: the current implementation runs **on top of Spring Boot**. Spring Boot is the infrastructure, not the framework. The same hexagonal model — ports, plugins, and a runtime home — is meant to sit on other hosts later (**Quarkus**, **Micronaut**, and similar). Those hosts are not shipped here yet.

You build an API by adding:

1. **Plugins** — entry adapters, use-case processors, and client adapters (plus DTOs and datasources). These should depend on framework contracts, not on Spring (or any other host).
2. **A runtime home** (`PORTO_API_HOME`) — config, plugin YAML, Liquibase, OpenAPI, data, and logs

The same application runs by pointing `PORTO_API_HOME` at that directory. A future Quarkus or Micronaut host should load the same plugins and the same runtime home.

## Architecture

```
Entry Adapters  →  Business Processors  →  Client Adapters
   (REST/WS/…)         (use cases)           (DB/REST/FTP/…)
```

- **Entry adapters** accept HTTP (or other inbound) traffic and register routes. They do not contain business rules.
- **Processors** implement use cases. They have no HTTP knowledge.
- **Client adapters** talk to databases and other systems. JDBC adapters must not embed SQL in Java; named statements live in plugin YAML in the runtime home.

Shared hexagonal contracts (`EntryAdapter`, `ClientAdapter`, `BusinessProcessor`) live in **porto-core**. The host-agnostic SPI that plugins compile against (`HostContext`, `RequestContext`, `RouteRegistrar`, `Persistence`, `JobScheduler`, `ScheduledJob`, `IdentityProvider`, `AccessTokenService`) lives in **porto-api-common**.

This git tree is the **Spring Boot host only**. It wires the framework to Spring (HTTP, DI, logging, process lifecycle). It does not contain your domain.

### Hosts

| Layer | Role | Today | Later |
|-------|------|--------|--------|
| Framework | Hexagonal ports, plugin loader, runtime home | porto-core + porto-api-common + this model | unchanged |
| Host | Process, HTTP server, config bootstrap | **Spring Boot 4.1.1** (this repo) | Quarkus, Micronaut, … |
| Application | Your APIs | plugins + `PORTO_API_HOME` | same plugins and home, different host |

The host is not fully abstract yet: this tree still uses Spring Boot APIs and `application.yml`. The target is that application plugins and the runtime home stay portable while a new host module replaces this one.

## Requirements

- Java 21 (LTS)
- This host: Spring Boot 4.1.1 (`spring-boot-starter-parent` in `pom.xml`)
- Maven
- porto-core installed in your local Maven repository
- A runtime home directory (`PORTO_API_HOME`)
- [direnv](https://direnv.net/) (recommended) so the current app is set when you `cd`

## Quick start

With direnv (see [Current app (direnv)](#current-app-direnv)):

```bash
cd ~/projects/porto-workspace          # loads PORTO_API_APPLICATION + PORTO_API_HOME
cd porto-api-plugins && mvn package    # shared + current-app plugins only
cd ../hexagonalboot-api && ./mvnw spring-boot:run
```

Without direnv, package plugins into a runtime home, then start **this Spring Boot host**:

```bash
export PORTO_API_APPLICATION=shine-media
cd /path/to/your-plugins
mvn package

export PORTO_API_HOME=/path/to/your-api-deploy
export API_ENV=dev
export LOG_PATH=$PORTO_API_HOME/logs
cd /path/to/hexagonalboot-api && ./mvnw spring-boot:run
```

Starting the host without `PORTO_API_HOME` (or the legacy alias `API_HOME`) fails: this working tree is not a runtime home.

Default health check:

```
GET http://localhost:9091/api/v1.0.0/health
```

## Current app (direnv)

`porto-workspace` is a **directory on disk, not a git repository**. Clone the
host, plugins, and deploy repos into it yourself. The workspace `.envrc` is
therefore **local** — it is not committed anywhere. Copy the template from this
repo after you create the folder.

`PORTO_API_HOME` on an IntelliJ Application run configuration is only used when
you **run the host**. Maven `package` (plugin JAR copy) does not see that env.

Use **direnv** for the app you are developing. It exports `PORTO_API_APPLICATION`
and `PORTO_API_HOME` in the shell (and in IntelliJ if you install the
[Direnv plugin](https://plugins.jetbrains.com/plugin/15285-direnv)).

### Create the workspace (once per machine)

```bash
mkdir -p ~/projects/porto-workspace
cd ~/projects/porto-workspace

git clone git@gitlab.com:shine-group114134/hexagonalboot-api.git
git clone git@gitlab.com:shine-group114134/porto-api-plugins.git
git clone git@gitlab.com:shine-group114134/shine-media-api-deploy.git
git clone git@gitlab.com:shine-group114134/registry-api-deploy.git

# porto-core stays outside the workspace
# git clone git@gitlab.com:shine-group114134/porto-core.git ~/projects/porto-core

# Local only — do not git init this folder
cp hexagonalboot-api/docs/porto-workspace.envrc .envrc
```

You should have:

```
~/projects/porto-workspace/             # not a repo; has .idea/ and .envrc
├── .envrc                              # you created this; current app
├── hexagonalboot-api/                  # this host
├── porto-api-plugins/
├── shine-media-api-deploy/
└── registry-api-deploy/
```

### Enable direnv

```bash
brew install direnv
# bash: eval "$(direnv hook bash)"  in ~/.bash_profile
# zsh:  eval "$(direnv hook zsh)"   in ~/.zshrc

cd ~/projects/porto-workspace && direnv allow
cd hexagonalboot-api && direnv allow
cd ../porto-api-plugins && direnv allow
cd ../shine-media-api-deploy && direnv allow
cd ../registry-api-deploy && direnv allow
```

| Directory | In git? | What it sets |
|---|---|---|
| `porto-workspace/.envrc` | **No** — copy from [`docs/porto-workspace.envrc`](docs/porto-workspace.envrc) | Current app (`PORTO_API_APPLICATION`). Edit, then `direnv allow`. |
| `{app}-api-deploy/.envrc` | Yes | That deploy: `PORTO_API_HOME=$PWD` |
| `porto-api-plugins/.envrc` | Yes | `source_up` — inherits the workspace current app |
| `hexagonalboot-api/.envrc` | Yes | `source_up` — same, so `./mvnw spring-boot:run` has a home |

Switch app by editing `porto-workspace/.envrc` (`shine-media` or `registry`) and
running `direnv allow`, or `cd` into that `{app}-api-deploy`. Shared plugins
copy into the current home; the other app’s plugins do not.

Override in a gitignored `.envrc.local` next to a **repo** `.envrc` if needed.
Do not commit the workspace `.envrc`.

## Runtime home

`PORTO_API_HOME` is the product on disk. A typical layout:

```
your-api-deploy/                    # PORTO_API_HOME
├── config/
│   ├── api-dev.yml / api-prod.yml  # selects the application folder
│   └── {app}/                      # app YAML, adapter registries, Liquibase, OpenAPI
├── plugins/{layer}/{app}/          # app plugin YAML (tracked) + JARs (usually gitignored)
├── plugins/{layer}/shared/         # infrastructural plugin JARs (email, storage, e-sign, render)
├── plugins/common/                 # shared contract JARs
├── data/{app}/
├── logs/
├── bin/start.sh                    # optional
├── Dockerfile                      # optional
└── docker-compose.yml              # optional
```

Plugin JARs and per-plugin YAML are scoped `plugins/{type}/{application}/`. Infrastructural adapters also copy JARs to `plugins/{type}/shared/`. Shared contracts stay in `plugins/common/`. The host loads the active application’s folder first, then `shared`.

## Configuration hierarchy

Configuration is **layered**. Later layers override earlier ones for the same property key. Layers 3 and 4 live in the **runtime home** and should stay host-agnostic. Layers 1 and 2 are how **this Spring Boot host** bootstraps; a Quarkus or Micronaut host would replace those, not the runtime home files.

```
src/main/resources/application.yml            # 1. Spring Boot host bootstrap
        ↓
src/main/resources/application-{env}.yml      # 2. Host environment profile (dev, prod, …)
        ↓
$PORTO_API_HOME/config/api-{env}.yml          # 3. Framework config for this application
        ↓
$PORTO_API_HOME/config/{app}/{app}-{env}.yml  # 4. Application-specific overrides (wins)
```

Each runtime home is **one application**. Switching applications means pointing `PORTO_API_HOME` at a different directory, not editing a switch inside the host.

### Layer 1 — `application.yml` (Spring Boot host)

Classpath defaults for this host:

- `spring.application.name: porto-api` (overridden per app at layer 4)
- Active profile: `${API_ENV:dev}`
- Server port, Actuator, shared paths (`porto.api.home-directory`, `config-path`, `plugins-path`)

### Layer 2 — `application-{env}.yml` (Spring Boot environment)

Imports the framework config file for that environment from the runtime home:

- `application-dev.yml` → `$PORTO_API_HOME/config/api-dev.yml`
- `application-prod.yml` → `$PORTO_API_HOME/config/api-prod.yml`

### Layer 3 — `config/api-{env}.yml` (framework, in the runtime home)

Selects the application folder:

```yaml
porto:
  api:
    application-path: ${porto.api.home-directory}/config/{app}
```

The framework file imports only that app’s `{app}-{env}.yml`.

### Layer 4 — `config/{app}/{app}-{env}.yml` (application, in the runtime home)

Highest priority. Overrides framework and host defaults (datasource, application name, …).

## Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORTO_API_APPLICATION` | (direnv / Maven) | App you are packaging for: `shine-media` or `registry`. Derives `PORTO_API_HOME` as `{workspace}/{app}-api-deploy`. |
| `PORTO_API_HOME` | **required** to start the host | Runtime home: `config/`, `plugins/`, `data/`, documents. `API_HOME` is a legacy alias. Wins over application-derived path. |
| `API_ENV` | `dev` | Environment (`dev`, `prod`, …). This host maps it to a Spring profile. |
| `LOG_PATH` | `${PORTO_API_HOME}/logs` | Directory for `porto-api-application.log` (this host also reads `LOGGING_FILE_PATH`) |
| `LOG_LEVEL` | `INFO` | Root log level (this host also reads `LOGGING_LEVEL_ROOT`) |

Resolved paths (from this host’s `application.yml`):

| Property | Value |
|----------|-------|
| `porto.api.config-path` | `${PORTO_API_HOME}/config` |
| `porto.api.application-path` | set in the runtime home’s `api-{env}.yml` |
| `porto.api.plugins-path` | `${PORTO_API_HOME}/plugins` |

The application id is derived from the last segment of `porto.api.application-path` when not set explicitly.

## Run in IntelliJ

These steps start the **Spring Boot host**.

1. **File → Open** the folder that contains this host (and, if you keep them as siblings, your plugin reactor and runtime home). Import **this** `pom.xml` and your plugin reactor `pom.xml` as **separate** Maven projects. Do **not** add a runtime home with the Maven **+** button — it is `PORTO_API_HOME`, not a build.
2. **Project SDK / language level:** Java 21.
3. Package plugins from `porto-api-plugins` (`mvn package`). direnv or
   `porto-api-home.properties` (`porto.api.application=shine-media`) selects the
   home. The Application run configuration does **not** feed Maven.
4. Create an **Application** run configuration (or use the gutter run on `systems.porto.api.HexagonalBoot`):
   - **Use classpath of module:** `HexagonalBoot-API`
   - **Main class:** `systems.porto.api.HexagonalBoot`
   - **Working directory:** this host tree (not the runtime home)
   - **Environment:** install the Direnv plugin so `.envrc` is applied, **or** set
     variables (semicolon-separated; IntelliJ expands `$USER_HOME$`):

```
PORTO_API_HOME=$USER_HOME$/projects/porto-workspace/shine-media-api-deploy;API_ENV=dev;LOG_PATH=$USER_HOME$/projects/porto-workspace/shine-media-api-deploy/logs;LOG_LEVEL=INFO
```

Without `PORTO_API_HOME` the host treats the working directory as the runtime home and fails.

After changing a plugin, package it again so the runtime home’s `plugins/` JARs update, then restart the run configuration. Stop the host before replacing plugin JARs: the plugin `URLClassLoader` keeps the zip open; overwriting a running JAR surfaces as `ZipException: invalid LOC header`.

## Databases

An application may use **one or many** databases. Each database is a named resource with its own connection pool, Liquibase changelog, and client adapters that bind to it by id.

Datasource plugins register pools as `datasources.{id}`. JDBC client adapters select the database via plugin config (`datasource`, `sql.dialect`). Changelog paths are **relative to `PORTO_API_HOME`** (for example `config/{app}/db/{database-id}/changelog/db.changelog-master.yaml`).

On this host, Spring Boot Liquibase is disabled per app (`spring.liquibase.enabled: false`) so every database is migrated the same way by its datasource plugin.

### SQL outside Java (dialects) — mandatory

JDBC adapters MUST **not** embed SQL in Java. Named statements live in the client-adapter plugin YAML in the **runtime home**. Platform loaders live in **porto-api-common**.

## Plugins

Plugin **registries** (which JARs to load) live in YAML under the application config directory in the runtime home.

| File | Plugin type | Per-plugin config path |
|------|-------------|------------------------|
| `dtos-dev.yaml` | Contract JARs (`porto-api-common`, DTOs) | — (JAR only; under `plugins/common` or `plugins/dtos/`) |
| `datasources-dev.yaml` | Datasource plugins | `plugins/datasources/{id}-{env}.yaml` |
| `entry-adapters-dev.yaml` | Inbound (REST, WebSocket, …) | `plugins/entry-adapters/{id}-{env}.yaml` |
| `processors-dev.yaml` | Business logic | `plugins/processors/{id}-{env}.yaml` |
| `client-adapters-dev.yaml` | Outbound (DB, REST client, …) | `plugins/client-adapters/{id}-{env}.yaml` |
| `scheduled-jobs-dev.yaml` | Cron work units (`ScheduledJob`) | optional `plugins/scheduled-jobs/{id}-{env}.yaml` |

Load order: **common/dtos → datasources → client-adapters → processors → entry-adapters → scheduled-jobs**.

Each entity typically has its own REST entry-adapter module that registers that entity’s HTTP paths at `start()`. Processors have no HTTP knowledge.

An EXTERNAL identity provider (`keycloak-idp`) reads `issuer`, `jwks-uri`, and
`token-uri` from **that plugin’s YAML** in the runtime home, not from this host’s
`application.yml`. The host fetches the JWKS public keys from `jwks-uri` and
caches them in memory. See `porto-shared-plugins/docs/plugins/keycloak-idp.md`.

### Building plugins

Plugin sources live in a **separate Maven reactor** (not this repo). On `mvn package`, each module copies its JAR to:

```
${porto.api.home}/plugins/{layer}/{application}/
${porto.api.home}/plugins/{layer}/shared/          # porto-shared-plugins
```

when `porto.api.home` is set. Resolution order: `PORTO_API_HOME` / `-Dporto.api.home`,
then `PORTO_API_APPLICATION` / `porto.api.application` (direnv or
`porto-api-home.properties`) → `{workspace}/{app}-api-deploy`. Shared plugins
always copy to that home. App-owned plugins copy only if they belong to that app.
If the home is empty, the copy is skipped.

### Module roles

| Module | Role | Depends on |
|--------|------|------------|
| `porto-api-common` | Shared SPI: `HostContext`, `RequestContext`, `RouteRegistrar`, `Persistence`, `JobScheduler`, `ScheduledJob`, `IdentityProvider`, `AccessTokenService` | porto-core |
| `porto-shared-plugins` | Infrastructural adapters plus shared auth (`local-idp`, `identity-store-jdbc`, `auth-token`). See `porto-api-plugins/porto-shared-plugins/docs/` | `porto-api-common` |
| `{app}-dtos` | DTOs + capability interfaces | `porto-api-common` |
| `*-jdbc` client adapters | Implement capabilities; SQL from plugin YAML by dialect | `{app}-dtos` |
| `*-crud` processors | Use cases only (no HTTP) | `{app}-dtos` / `porto-api-common` |
| `*-rest` entry adapters | That entity’s HTTP routes (`RouteRegistrar`) | `porto-api-common` |
| `scheduled-jobs/*` | Work units fired by persisted cron rows | `porto-api-common` only (no Quartz) |

Processors resolve client adapters by connector hook id (for example `DB`) with `requestContext.requireClientAdapter("DB", YourPersistence.class)`.

## Build

```bash
./mvnw test
# direnv already exported PORTO_API_HOME, or:
PORTO_API_HOME=/path/to/your-api-deploy ./mvnw spring-boot:run
```

Install porto-core locally before building the host.

Host integration tests use fixtures under `src/test/resources/runtime-fixtures/` so CI does not need a full runtime home. Plugin JARs in those fixtures are typically gitignored; package plugins into the fixture (or copy JARs) before `./mvnw test` if they are missing.

## Updating Java and the Spring Boot host

This section is for **this host**. A Quarkus or Micronaut host would have its own version file.

Versions live in this repo’s `pom.xml`. Current baseline: **Java 21** and **Spring Boot 4.1.1**. Do not pair 4.1.x with Java 17.

| What | Where |
|------|--------|
| Spring Boot | `<parent>` → `spring-boot-starter-parent` `<version>` |
| Java (compile + release) | `<properties>` → `<java.version>` |
| Springdoc (pinned; not managed by the parent) | `springdoc-openapi-starter-webmvc-ui` `<version>` (now `3.1.0`) |
| Plugin compile target | your plugin reactor `pom.xml` → `<maven.compiler.source>` / `<maven.compiler.target>` |
| Docker JRE | your runtime home `Dockerfile` → `FROM eclipse-temurin:21-jre` |
| IntelliJ SDK | **File → Project Structure → Project** → SDK and language level **21** |

Stay on a Java version that the Spring Boot line supports. For 4.1.x that is **Java 21** (LTS). Prefer patch/minor updates on the current Boot generation before a major jump.

### Spring Boot (same generation, e.g. 4.1.1 → 4.1.x)

1. Pick a release from the [Spring Boot releases](https://github.com/spring-projects/spring-boot/releases) and read the notes for that line ([4.1](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)).
2. Set the parent version in `pom.xml`.
3. Check whether `springdoc-openapi-starter-webmvc-ui` still matches that Boot line. Host code still uses Jackson 2 (`ObjectMapper`); keep `spring-boot-jackson2` until that migrates to Jackson 3.
4. Keep `<java.version>` at **21**.
5. Reload Maven in IntelliJ, then `./mvnw -U test`.

`-U` forces a metadata refresh so Maven does not keep the old parent.

### Java (e.g. 21 → 25)

1. Install the JDK and point IntelliJ at it. Run configurations should use that SDK.
2. Set `<java.version>` in this `pom.xml` to the same major.
3. Set the plugin reactor compiler source/target to the same major.
4. Change your runtime home Dockerfile to the matching JRE tag.
5. `./mvnw -U test`, then start a product from IntelliJ or `./mvnw spring-boot:run`.

A Java bump without a Spring Boot bump is fine if Boot still supports that JDK. A Spring Boot major (4 → 5) is not: read the migration guide, then re-check porto-core, `porto-api-common`, springdoc, Jackson (`spring-boot-jackson2` vs Jackson 3), and plugin compile target together.

## Related libraries

- **[Picollo](https://github.com/DinizR/picollo)** — earlier plugable Spring Boot platform (OSGi). HexagonalBoot replaces that model with ports and adapters.
- **porto-core** — hexagonal contracts: `EntryAdapter`, `ClientAdapter`, `BusinessProcessor`, `Context` (host-agnostic)
- **porto-api-common** — SPI that application plugins compile against (keep host-agnostic so the same JARs can run on Spring Boot, Quarkus, or Micronaut)

## License

Copyright 2026 Rodrigo Dinis. Licensed under the Apache License, Version 2.0.
See [LICENSE](LICENSE) and [NOTICE](NOTICE).
