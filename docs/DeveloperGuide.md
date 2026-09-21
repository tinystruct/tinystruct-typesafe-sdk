# Developer guide

## Adding a routable action

1. Write an ordinary tinystruct action and **declare every parameter** in `@Action(arguments = ...)`, in order:

   ```java
   @Action(value = "close-ticket",
           description = "Close and resolve an existing ticket, by its number.",
           arguments = {@Argument(key = "ticketId", type = "number", description = "The ticket number.")})
   public String closeTicket(int ticketId) { ... }
   ```

2. Add it to `typesafe.routing.allowed-actions`. If it is destructive, also to `typesafe.routing.confirm-actions`, and consider a stricter `typesafe.routing.min-confidence.<action>`.
3. Try it from the terminal, first without the model:

   ```bash
   bin/dispatcher close-ticket/42 --import my.HelpDesk
   ```

   then through the dispatcher.

Guidelines for what the model reads:

* Descriptions are literal instructions. State boundaries ("Close a ticket. Does not delete it.").
* Jev is weak at arithmetic, counting and dates. Take numbers as text and parse them in the action; do not ask the model to compute.
* Keep enum constants self-explanatory; list them in the parameter description.

## Running the pipeline in a test

`DispatchPipeline` takes everything through its constructor, so a test needs no application server and no network:

```java
MockTypesafeClient client = new MockTypesafeClient();
client.addChoiceAnswer("__tool__", "create-user", 0.95);
client.addChoiceAnswer("create-user.name", "John", 0.97);
client.addChoiceAnswer("create-user.role", "ADMIN", 0.96);

DispatchPipeline pipeline = DispatchPipeline.of(client, new TokenSpanExtractor(),
        new ConfidencePolicy(0.80, Double.NaN, 0.5), new ArgumentValidator(),
        new PathActionExecutor(), /* confirmation */ null, new DefaultPrincipalResolver(),
        settings, new DispatchMetrics());

DispatchResult result = pipeline.route("create an admin account for John", ActionRegistry.getInstance());
```

Install your application into the real registry first, exactly as `--import` would:
`ApplicationManager.install(new MyApp(), new Settings())`.

Answer keys: `__tool__`, `<action>.<param>`, `<action>.<param>?` (optional: was it stated), `<action>.<param>.<MEMBER>` (set member). Reserved options: `__other__` (no action applies) and `__none__` (not stated).

### Do not use `Settings.set` in tests

tinystruct's `Settings` is one process-wide property store, and `set` rewrites `application.properties` when one is on the classpath. Tests that set values through it leak into each other and can modify files. Use an isolated `Configuration<String>` (see `MapConfiguration` in the core tests).

## Implementing a `ConfirmationService`

```java
public class MyService implements ConfirmationService {
    public MyService() {}                                   // created by class name
    @Override public void configure(Configuration<String> config) { ... }
    @Override public String open(PendingCall call) { ... }  // store; return an id
    @Override public DispatchResult confirm(String id, String principal) { ... }
    @Override public void reject(String id, String principal) { ... }
}
```

Set `typesafe.confirmation.service=my.MyService`. Contract:

* `confirm` and `reject` **must** verify `principal` equals `call.getPrincipal()` (throw `PrincipalMismatchException`) and that the call has not expired (`ConfirmationExpiredException`).
* A second `confirm` must not run the action again (`ConfirmationConflictException`).
* To run the call, use `ConfirmedCallRunner` (`TypesafeRuntime.shared(config).confirmedCalls()`): it re-checks the allowlist, mode and arguments.
* Do not keep the arguments longer than needed; they can be personal data. `PendingCall.toString()` deliberately omits them.

## Implementing a `PrincipalResolver`

```java
public String resolve(Context context)   // stable identity of the caller, never derived from request parameters
```

The default reads a validated JWT (`CLAIMS`), then the session `userId`, then the session id, else `cli`. Anything you return must be something the caller cannot choose.

## Operating it

* **Pin the model** (`typesafe.model=jev-1.13.0`). The cache key includes the model name, so a pinned model keeps entries valid and an alias change misses cleanly.
* **Cache**: `memory` for one process, `redis` for several. Only classifications are cached, never results of executed actions. A change to the allowlist or to any description changes the request, so it misses on its own.
* **Snapshots**: use `file`, `redis` or `database` outside tests, restrict access, and encrypt at rest. Untouched pending calls stay until removed; a periodic clean-up of old snapshots is worth adding.
* **Rate limits**: 429 and 529 are retried with backoff (`typesafe.retry-max`, `typesafe.retry-backoff-ms`). `typesafe/metrics` reports upstream calls, failures, cache hits, tokens, routing outcomes, confidence buckets, per-action counts and confirmation outcomes.
* **Logs** never contain argument values unless `typesafe.logging.log-arguments=true`, and never the API key.
* **Cost**: one request carries every question. With many allowlisted actions use `typesafe.routing.strategy=two-stage`; a choice question holds at most 255 options.

## Testing this project

```bash
mvn verify
```

`client`, `core` and `workflow` must stay at 90% line coverage. `RedisRoutingCache` needs a live Redis and is not exercised by the unit tests.
