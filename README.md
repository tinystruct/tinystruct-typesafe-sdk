# tinystruct-typesafe

Lets natural language invoke existing tinystruct `@Action` methods, using **TypeSafe Jev** as a semantic dispatcher.

> "Code owns the workflow; the model supplies programmable common sense."

This is **not** a chatbot. There is no prompt API and no chat abstraction. Jev answers typed questions by returning a probability distribution over options you supply. It never generates text or values, so every argument the action receives is either an enum constant, a boolean, or a verbatim span of the user's own input.

```
bin/dispatcher semantic --input "create an admin account for John"
   → create-user, name = John, role = ADMIN   (confidence 0.95)   → EXECUTED
```

## Modules

| Module | Purpose |
|---|---|
| `tinystruct-typesafe-client` | `TypesafeClient` over `POST /v1/systemone`, `RoutingRequest`/`RoutingResult`, `MockTypesafeClient` |
| `tinystruct-typesafe-core` | The dispatch pipeline, question generation, policies, cache, metrics, the `semantic` actions |
| `tinystruct-typesafe-workflow` | `ConfirmationService` on `tinystruct-workflow`: a pending call is a suspended, persisted workflow execution |
| `tinystruct-typesafe-demo` | User management, CRM and help desk example applications |

`core` does not depend on `tinystruct-workflow`. Without the workflow module, an action that needs confirmation is refused rather than run.

## Requirements

* Java 17
* **tinystruct 1.7.34 or later.** This project relies on a small framework change (see [Architecture.md](docs/Architecture.md#the-tinystruct-change)): `@Action(arguments = ...)` metadata now keeps order, `optional` and the parameter's Java type (as the argument's `type`), and string arguments convert to `Set`/`List` of enums. 1.7.34 is not on Maven Central yet (the latest there is 1.7.33), so until it is published install it locally with `mvn install` in the `tinystruct` project.
* A TypeSafe API key.

## Making an action routable

An action becomes routable when it is on the allowlist **and** every parameter is declared in `@Action(arguments = ...)`:

```java
@Action(value = "create-user",
        description = "Create a new user account with a name and a role.",
        arguments = {
            @Argument(key = "name", description = "The user's name."),
            @Argument(key = "role", description = "The role: ADMIN, EDITOR or VIEWER.")
        })
public String createUser(String name, Role role) { ... }
```

The description is what the model reads, so write it for the model: say what the action does, and what it does not.

| Parameter type | Asked as |
|---|---|
| `enum` | one `choice` question over the constants |
| `boolean` | one `noul` question |
| `Set<Enum>` / `List<Enum>` | one `noul` question per constant |
| `String`, numbers, `Date` | a `choice` over spans of the input, with a "not stated" option |

Things to know:

* **Arguments bind by position** (tinystruct's own rule), so a parameter can only be left out at the end, by providing an overload with fewer parameters. An optional parameter followed by a supplied one is refused.
* Values may not contain `/`, because tinystruct binds arguments from path segments.
* Overloads of an allowlisted action are not supported (the framework keeps one command description per name).
* Path templates (`user/{id}`) and built-ins (`start`, `generate`, ...) are never routable.
* An action declared for one mode (`HTTP_POST`, `CLI`, ...) is only routable from that mode.

## Configuration

`application.properties`:

| Key | Default | |
|---|---|---|
| `typesafe.api-key` | env `TYPESAFE_API_KEY` | required |
| `typesafe.model` | `jev-latest` | pin a version (`jev-1.13.0`) in production |
| `typesafe.endpoint` | `https://api.typesafe.ai/v1/systemone` | |
| `typesafe.routing.allowed-actions` | *empty* | **comma-separated; nothing is routable until set** |
| `typesafe.routing.confirm-actions` | *empty* | always wait for a human, e.g. `delete-user` |
| `typesafe.routing.min-confidence` | `0.80` | below this: refused (`AmbiguousIntentException`) |
| `typesafe.routing.min-confidence.<action>` | | per-action minimum |
| `typesafe.routing.auto-confidence` | *off* | from the minimum up to this, ask for confirmation |
| `typesafe.routing.set-threshold` | `0.5` | a set member is included at or above this probability |
| `typesafe.routing.strategy` | `single` | `two-stage` sends the action question first, then only its arguments |
| `typesafe.routing.confirmation-timeout-seconds` | `300` | |
| `typesafe.validation.max-argument-length` | `200` | |
| `typesafe.cache.provider` | `memory` | `none`, `memory`, `redis` |
| `typesafe.cache.ttl` | `3600` | seconds |
| `typesafe.confirmation.service` | *none* | class name, e.g. `org.tinystruct.typesafe.workflow.WorkflowConfirmationService` |
| `typesafe.principal.resolver` | built-in | class name of a `PrincipalResolver` |
| `typesafe.workflow.repository` | `memory` | `memory` (tests), `file`, `redis`, `database` |
| `typesafe.workflow.snapshot-dir` | `workflow-snapshots/` | for `file` |
| `typesafe.workflow.keep-completed` | `false` | keep finished snapshots for audit |
| `typesafe.logging.log-arguments` | `false` | argument values can be personal data |
| `typesafe.connect-timeout-ms`, `typesafe.read-timeout-ms`, `typesafe.retry-max`, `typesafe.retry-backoff-ms` | 5000, 30000, 3, 1000 | 429 and 529 are retried with backoff |

Redis settings are tinystruct's own: `redis.host`, `redis.port`, `redis.password`.

## Running

Everything runs through `bin/dispatcher`; there is no `main()`. The demo module is the runnable one:

```bash
mvn package                                   # builds everything and copies the demo's runtime jars into tinystruct-typesafe-demo/lib
export TYPESAFE_API_KEY=...                   # or typesafe.api-key in application.properties
cd tinystruct-typesafe-demo
bin/dispatcher semantic --input "create an admin account for John"      # Windows: bin\dispatcher.cmd
```

`bin/dispatcher` puts only `target/classes`, `lib/*.jar` and the tinystruct jar on the classpath, so **every module the applications use must be in `lib/`**. That is what `mvn package` does for the demo. Running a launcher from `core/` or `workflow/` fails with `NoClassDefFoundError: org/tinystruct/typesafe/client/TypesafeClient`, because the sibling modules are not on its classpath.

The applications are loaded by `default.import.applications` in the demo's `application.properties` (tinystruct's own setting, `;`-separated), so no `--import` is needed. Without that setting, pass one `--import <class>` per application.

The instruction is the `--input` option, not a path segment, so it may contain spaces and slashes. Over HTTP pass it as the `--input` query parameter. On Windows, run it from PowerShell or `cmd`; Git Bash's quoting splits a multi-word `--input` when it calls `bin\dispatcher.cmd`, and its `bin/dispatcher` shell script cannot start the JVM at all.

| Command | |
|---|---|
| `semantic --input "..."` | run the pipeline. Result JSON: `status` is `EXECUTED`, `NEEDS_CONFIRMATION` (with `pendingId`) or `REJECTED` (with `reason`) |
| `semantic/confirm/<pendingId>` | confirm a held call |
| `semantic/reject/<pendingId>` | cancel a held call |
| `typesafe/metrics` | counters as JSON |

Each `bin/dispatcher` call is its own JVM (so `typesafe/metrics` counts only that call), and command-line use needs `typesafe.workflow.repository=file` (or `redis`/`database`); `memory` cannot carry a pending call from one command to the next.

## Security

1. **Opt-in allowlist.** Only allowlisted actions are offered to the model or routable. The default is empty.
2. **Values come from the input.** A free-text argument is always one of the spans that were offered; a value the model made up is refused. The model never writes an argument.
3. **Validation.** Every resolved argument is checked again (presence, enum membership, type, length, control characters, `/`) before anything runs, and again when a confirmed call finally executes.
4. **Confidence.** Below the minimum nothing runs. The score is the weakest link across the action choice and every argument used.
5. **Confirmation, failing closed.** A confirm-action, or a call in the confirm tier, is held. If no `ConfirmationService` is configured it is refused, never run.
6. **Confirm and reject are authorization.** They require the same principal that opened the call: a validated JWT subject, the session `userId`, or the HTTP session itself (`cli` on the command line). Request parameters are never trusted for identity.
7. **Mode and path safety.** The framework enforces each action's mode, and the dispatcher checks the call resolved to the allowlisted action, not another whose pattern also matched.
8. **Injection.** Jev does not resist instructions inside the input. The controls above are what bound the damage: an injected instruction can at worst select another *allowlisted* action, and destructive ones wait for a human.

## Data at rest

A pending call's snapshot holds its arguments. It is deleted as soon as the call is confirmed, rejected, expired or failed (unless `typesafe.workflow.keep-completed=true`). A call that is never touched again stays until removed. Restrict access to the snapshot directory (`file`) or the store (`redis`, `database`) and encrypt it at rest.

## Building

```bash
mvn verify
```

Runs the tests of all four modules and enforces 90% line coverage on `client`, `core` and `workflow`. The tests use the real `ActionRegistry`, real actions and a real workflow engine; only TypeSafe is simulated.

See [docs/Architecture.md](docs/Architecture.md) and [docs/DeveloperGuide.md](docs/DeveloperGuide.md).
