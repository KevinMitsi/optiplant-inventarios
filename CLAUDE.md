# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This repository is a **Spring Boot skeleton** for "optiplant-inventarios", a multi-branch (multi-sucursal)
inventory management system. Right now the codebase is only the Spring Initializr scaffold (`InventoriesApplication`,
one config file, no controllers/entities/repositories yet, no Flyway migrations yet). The real content of this repo
so far is the design documentation in `docs/`:

- `docs/PHASE1.md` — planning phase: problem statement, actors, epics, 42 user stories, 47 functional requirements,
  12 non-functional requirements, 13 core business rules (RN-01..RN-13), MVP prioritization.
- `docs/ENTITIES.md` — the full data model (3NF), table-by-table with columns, constraints, FKs, and rationale.

Both docs are written in Spanish and are the source of truth for domain behavior. Read the relevant section before
implementing a feature rather than guessing at requirements — e.g. before adding an entity, check `ENTITIES.md` for
its already-designed columns/constraints; before implementing a use case, check `PHASE1.md` for the matching HU and
RN. Note that `PHASE1.md` itself lists stack/architecture decisions (DB engine, REST vs GraphQL, ORM, auth mechanism)
as "pending" at planning time — but `build.gradle` reflects that those decisions have since been made (see below), so
treat `build.gradle`/actual code as authoritative over the docs whenever they conflict on *technical* choices; treat
the docs as authoritative on *domain/business* rules until corresponding code exists.

## Commands

The project uses the Gradle wrapper — always invoke `./gradlew` (or `gradlew.bat` on native Windows shells), not a
global `gradle`.

```bash
./gradlew build              # compile, run tests, assemble
./gradlew test                # run the test suite (JUnit 5 / Testcontainers)
./gradlew test --tests "io.github.KevinMitsi.inventories.InventoriesApplicationTests"   # single test class
./gradlew test --tests "*.InventoriesApplicationTests.contextLoads"                     # single test method
./gradlew bootRun            # run the application locally
./gradlew asciidoctor        # generate API docs from Spring REST Docs snippets (depends on `test`)
```

Integration tests use Testcontainers to spin up a real PostgreSQL container (`TestcontainersConfiguration`) via
`@ServiceConnection` — Docker must be running locally to run the test suite. `TestInventoriesApplication` is a
dev-time main class that boots the app wired to that same Testcontainers Postgres (useful for `bootTestRun`) instead
of a manually configured local database.

`docker compose up` starts a standalone PostgreSQL instance (`compose.yaml`) for manual/local runs; Spring Boot's
`spring-boot-docker-compose` module (a `developmentOnly` dependency) will auto-detect and start this when running
`bootRun` locally.

## Architecture

Tech stack actually locked in via `build.gradle` (Java 21 toolchain):

- **Web**: `spring-boot-starter-web` (REST, per RT-02 — no business logic in the frontend).
- **Persistence**: `spring-boot-starter-data-jpa` + PostgreSQL driver, migrations managed with **Flyway**
  (`spring-boot-starter-flyway`) — put new migrations under `src/main/resources/db/migration` following Flyway's
  `V{n}__description.sql` naming; none exist yet, so the first migration will need to establish the baseline schema
  described in `docs/ENTITIES.md`.
- **Security**: `spring-boot-starter-security` + JJWT (`io.jsonwebtoken`) for token-based auth.
- **API docs**: Spring REST Docs + Asciidoctor — documentation snippets are generated from tests (`outputs.dir
  snippetsDir` on the `test` task), so controller tests are expected to produce the REST Docs snippets consumed by
  the `asciidoctor` task.
- **Lombok** is available for both main and test source sets.

### Domain model

The system models inventory across multiple branches (`sucursales`) belonging to one organization. The entity
relationship overview (`docs/ENTITIES.md` §4) is:

```text
ORGANIZATION ──< BRANCH ──< USER >── ROLE
                       ├──< INVENTORY >── PRODUCT ── CATEGORY
                       │                       └──< PRODUCT_UNIT >── UNIT_OF_MEASURE
                       ├──< INVENTORY_MOVEMENT
                       ├──< PURCHASE_ORDER >── SUPPLIER ──< PURCHASE_ORDER_ITEM >── PRODUCT
                       ├──< SALE ──< SALE_ITEM >── PRODUCT
                       └──< TRANSFER ──< TRANSFER_ITEM, TRANSFER_STATUS_HISTORY, TRANSFER_ISSUE
```

Plus: `PRICE_LIST >── PRODUCT_PRICE`, `CARRIER ──< TRANSFER`, `LOGISTICS_ROUTE` (origin/destination branch),
`INVENTORY ──< INVENTORY_ALERT`.

Core entities to implement first (per `PHASE1.md` §33): `Organization, Branch, User, Role, Product, Category,
UnitOfMeasure, ProductUnit, Inventory, InventoryMovement, Supplier, PurchaseOrder, PurchaseOrderItem, Sale, SaleItem,
Transfer, TransferItem, TransferStatusHistory`. Second-level: `PriceList, ProductPrice, Carrier, LogisticsRoute,
TransferIssue`.

### Non-negotiable domain invariants (see `PHASE1.md` §22 for the full list)

- **Stock never changes without a movement** (RN-04): every stock change on `inventory` must be backed by a row in
  `inventory_movement`. This is the central invariant of the domain.
- Stock belongs to the `(branch, product)` pair, not to the product alone (RN-02).
- A sale must never be confirmed if `cantidadSolicitada > stockDisponible` (RN-03).
- A transfer must have `origin != destination` (RN-07) and can only be dispatched if there is sufficient stock for
  the approved quantity (RN-08); a transfer's received quantity must reflect exactly what physically arrived (RN-09),
  and shortages become `TransferIssue` records rather than silently vanishing (RN-10).
- Historical/confirmed movements are immutable — correct mistakes with a new adjustment movement, never by mutating
  a past movement (RNF-12).
- Monetary and inventory quantities are `DECIMAL`, never `FLOAT`/`float` (fractional units like kg/L/m must be
  representable).
- Role-based authorization matters: an admin sees all branches; a branch manager operates primarily within their own
  branch (RN-12/RN-13).

When adding a table/entity, check `docs/ENTITIES.md` for the already-designed columns, constraints (UNIQUE, CHECK),
and FK relationships for that entity before inventing your own — most of the schema has already been designed to
3NF, including the reasoning for what's denormalized on purpose (e.g. why `inventory.quantity` is stored as a
maintained aggregate alongside the append-only `inventory_movement` log — `ENTITIES.md` §8.2).
