# Architecture

## The flow

```
bin/dispatcher semantic --input "delete user John"
        │
        ▼
SemanticDispatcher            thin @Action adapter (semantic, semantic/confirm, semantic/reject, typesafe/metrics)
        │
        ▼
TypesafeRuntime               composition root: builds and shares everything below from configuration
        │
        ▼
DispatchPipeline              decides and acts: policy and side effects
  │
  ├─ Interpreter              understands the instruction; never runs or holds anything
  │    1. ActionCatalog       allowlist → ActionDefinition, from tinystruct's own CommandLine metadata
  │    2. Classifier          SingleStage | TwoStage: QuestionGenerator → TypesafeClient
  │                           (client = CachingTypesafeClient → MeteredTypesafeClient → HttpTypesafeClient)
  │    3. ArgumentResolver    answers → typed arguments (enum, boolean, Set, verbatim span)
  │    4. ArgumentValidator   presence, membership, type, length, control characters, '/'
  │    5. ConfidencePolicy    weakest-link score
  │         └─► Interpretation   a validated call, or a refusal with a reason
  │
  ├─ ConfidencePolicy         score → ACT | CONFIRM | AMBIGUOUS (per-action minimums)
  ├─ ActionExecutor           PathActionExecutor → ApplicationManager.call("delete-user/John")
  └─ ConfirmationService      WorkflowConfirmationService.open(PendingCall) → NEEDS_CONFIRMATION + pendingId
```

Confirmation, later and possibly in another process:

```
semantic/confirm/<id> ─► ConfirmationHandler ─► WorkflowConfirmationService.confirm(id, principal)
     checks: exists · principal matches originator · WAITING · not expired
     engine.resume(id, ConfirmationEvent)                      (direct call, holds the DistributedLock)
        └► node semantic-confirm/execute ─► ConfirmedCallRunner
             re-check allowlist and mode · decode arguments · validate again · ActionExecutor
     snapshot deleted

semantic/reject/<id>  ─► same checks ─► engine.cancel(id) ─► snapshot deleted
```

## Layout

```
tinystruct-typesafe-client       org.tinystruct.typesafe.client
    TypesafeClient, HttpTypesafeClient, RoutingRequest, RoutingResult
    testing/         MockTypesafeClient (test support, shipped so other modules can use it)

tinystruct-typesafe-core         org.tinystruct.typesafe.core
    (root)           SemanticDispatcher, TypesafeRuntime          entry point and wiring; nothing depends on it
    api/             ActionSemanticRouter, DispatchResult         the public result API
    catalog/         ActionCatalog, ActionDefinition, ParameterDefinition, CallerMode
                     what can be routed, read from tinystruct's own command metadata
    candidate/       CandidateExtractor, TokenSpanExtractor, ValueCandidate
                     the closed set of spans a free-text argument is chosen from
    question/        QuestionGenerator                            catalog + candidates → TypeSafe questions
    argument/        ArgumentResolver, ArgumentValidator, ArgumentCodec, InvalidActionException
                     answers → typed, validated arguments (and their persisted form)
    policy/          ConfidencePolicy, ConfidenceScore, AmbiguousIntentException
    pipeline/        DispatchPipeline, Interpreter, Interpretation, Classifier (+ SingleStage, TwoStage)
    execution/       ActionExecutor, PathActionExecutor           running a validated call through tinystruct
    confirmation/    ConfirmationService, ConfirmationHandler, ConfirmedCallRunner, PendingCall,
                     PrincipalResolver, DefaultPrincipalResolver, the confirmation exceptions
    cache/           RoutingCache, MemoryRoutingCache, RedisRoutingCache, CachingTypesafeClient
    metrics/         DispatchMetrics, MeteredTypesafeClient
    config/          TypesafeConfig (the keys), RoutingSettings (the values)

tinystruct-typesafe-workflow     org.tinystruct.typesafe.workflow
    WorkflowConfirmationService, SemanticConfirmApplication, ConfirmationEvent, EngineHolder, PendingCallSerializer

tinystruct-typesafe-demo         org.tinystruct.typesafe.demo
    UserManagementApplication, CrmApplication, HelpDeskApplication
```

Dependencies inside `core` point one way, with no cycles. `catalog` depends on nothing; `candidate`, `question` and `argument` build on it in that order; `config` and `policy` build on those, `api` on `policy`, and `execution`, `metrics`, `cache` and `confirmation` above them; `pipeline` composes the lot, and the root wires everything from `config`. `PackageStructureTest` fails the build if a cycle appears or anything imports from the root.

Tests live next to what they test, and shared test support is in `core/.../testing` (`TestApps`, `MapConfiguration`, `MockClientRuntime`).

## The tinystruct change

The extension needs to know, for each action, its parameters' **order, optional flag and Java type** (to turn an `enum` into a choice question, or `Set<Role>` into one question per constant). tinystruct already reads all of that from `@Action(arguments = ...)` but `AnnotationProcessor` kept only the key and description, in an unordered `HashSet`. So the framework was changed (in `tinystruct`, released as 1.7.34):

* `CommandArgument` gained a `type`: the parameter's Java type, generics included (`Set<Role>`). It replaces the free-text `@Argument.type` hint, which nothing read and which could disagree with the real type.
* `AnnotationProcessor.getCommandArguments` keeps declaration order, copies `optional`, and sets the argument's `type` to the generic parameter type, aligned by index with the method's data parameters (skipping `Request`/`Response`), exactly as `ActionRegistry` already aligns them.
* `Action` keeps the generic parameter types and converts a comma-separated string to `Set`/`List` of enums, so `bin/dispatcher assign-roles/John/ADMIN,VIEWER` works for anyone, not only through this extension.

With that, the extension has **no discovery code and no invocation code of its own**: `ActionCatalog` asks the registry for `getCommand(path, mode)` per allowlisted name, and `PathActionExecutor` hands the call to `ApplicationManager.call`, so mode checks, context binding and argument conversion are the framework's. The only logic in the executor is turning typed arguments into path segments, plus a check that the path resolved to the allowlisted action.

## Design (SOLID)

| Principle | How |
|---|---|
| **Single responsibility** | One class per concern: `ActionCatalog` (metadata), `QuestionGenerator` (questions), `ArgumentResolver` (answers → values), `ArgumentValidator`, `ConfidencePolicy`, `PathActionExecutor`, `ConfirmationHandler`, `TypesafeRuntime` (wiring). Understanding an instruction (`Interpreter`) is separate from deciding what to do with it (`DispatchPipeline`), so the interpreter can be tested with a stub classifier and no executor. `SemanticDispatcher` only adapts tinystruct and formats results. |
| **Open / closed** | Caching and metrics are decorators around `TypesafeClient` (`CachingTypesafeClient`, `MeteredTypesafeClient`); the pipeline knows nothing about them. How many requests a classification takes is a `Classifier` strategy (`SingleStageClassifier`, `TwoStageClassifier`), chosen by configuration. New cache providers, confirmation stores or principal sources are new classes selected by configuration. |
| **Liskov** | Every `TypesafeClient` (HTTP, mock, caching, metered) is interchangeable, as is every `RoutingCache`, `ConfirmationService`, `ActionExecutor`. |
| **Interface segregation** | Small, single-purpose interfaces: `TypesafeClient` (1 method), `CandidateExtractor`, `PrincipalResolver`, `RoutingCache`, `ActionExecutor`, `ConfirmationService`. |
| **Dependency inversion** | `DispatchPipeline` receives abstractions through its constructor and owns no I/O and no configuration. Only `TypesafeRuntime` knows concrete classes. Tests build a pipeline directly, with a mock TypeSafe client and the real registry. |

## Key decisions

1. **Synchronous pipeline, workflow only for confirmation.** Classification is short and needs the caller's own context (session, principal). Only the part that must survive a restart is a workflow: a validated call waiting for a human. Classification happens once, before the workflow starts, and is never repeated.
2. **Fail closed.** An unconfigured confirmation service means confirm-actions are refused. A configured class that cannot be created stops start-up. A missing model answer is a refusal, never read as a default.
3. **Values come from the input.** Jev returns options, not text. Free text is offered as a closed set of spans of the input; the chosen one must be one of them.
4. **Direct `engine.resume`, not `EventDispatcher`.** The event registry is in memory and does not know a call opened before a restart, and it swallows errors of the resumed execution. `engine.resume` loads the snapshot, takes the lock, and reports the result.
5. **The engine never deletes snapshots**, and they hold arguments, so `WorkflowConfirmationService` removes them through the repository when a call finishes.
6. **`engine.cancel` does not check status**, so it is only called on a `WAITING` call.
7. **Shared static state** (`TypesafeRuntime`, `EngineHolder`): tinystruct may create application instances per context, which would otherwise reset the cache and counters per request.
8. **Identity is not a request parameter.** `DefaultPrincipalResolver` reads only what tinystruct itself puts in the context (validated JWT claims, the session).

## Module dependencies

```
client  ◄── core  ◄── workflow ◄── demo
   │          │           │
   └── tinystruct   tinystruct-workflow (workflow only)
```

`core` has no dependency on `tinystruct-workflow`.

## Extension points

| Interface | Plug in by | Default |
|---|---|---|
| `TypesafeClient` | implement it and construct the pipeline (tests, alternative transports) | `HttpTypesafeClient` |
| `CandidateExtractor` | construct the pipeline with it | `TokenSpanExtractor` (spans of up to 3 tokens, at most 255) |
| `RoutingCache` | `typesafe.cache.provider` (`none`/`memory`/`redis`) | `MemoryRoutingCache` |
| `PrincipalResolver` | `typesafe.principal.resolver=<class>` | `DefaultPrincipalResolver` |
| `ConfirmationService` | `typesafe.confirmation.service=<class>` | none: confirm-actions are refused |
| `ActionExecutor` | construct the pipeline with it | `PathActionExecutor` |
| `Classifier` | `typesafe.routing.strategy` (`single`/`two-stage`), or build an `Interpreter` around your own | `SingleStageClassifier` |
| `SnapshotRepository` | `typesafe.workflow.repository` | memory |

## Limits

* Parameters bind by position, so only trailing parameters can be omitted (with a shorter overload). Overloaded allowlisted actions are not supported.
* Values cannot contain `/`.
* `Date` parameters are offered as text spans and parsed by tinystruct (`yyyy-MM-dd HH:mm:ss`); Jev is weak at dates, so do not rely on it for them.
* Multi-step chains ("create an account and then email them") are out of scope: Jev makes one judgment per question.
* There is no timer: expiry is checked when a call is confirmed or rejected. A call that is never touched again keeps its snapshot until removed.
