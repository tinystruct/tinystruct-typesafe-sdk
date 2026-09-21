package org.tinystruct.typesafe.core;

import org.tinystruct.ApplicationException;
import org.tinystruct.ApplicationRuntimeException;
import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.client.HttpTypesafeClient;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.api.ActionSemanticRouter;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.cache.CachingTypesafeClient;
import org.tinystruct.typesafe.core.cache.MemoryRoutingCache;
import org.tinystruct.typesafe.core.cache.RedisRoutingCache;
import org.tinystruct.typesafe.core.cache.RoutingCache;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.config.RoutingSettings;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.confirmation.ConfirmationHandler;
import org.tinystruct.typesafe.core.confirmation.ConfirmationService;
import org.tinystruct.typesafe.core.confirmation.ConfirmedCallRunner;
import org.tinystruct.typesafe.core.confirmation.DefaultPrincipalResolver;
import org.tinystruct.typesafe.core.confirmation.PrincipalResolver;
import org.tinystruct.typesafe.core.execution.ActionExecutor;
import org.tinystruct.typesafe.core.execution.PathActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.metrics.MeteredTypesafeClient;
import org.tinystruct.typesafe.core.pipeline.DispatchPipeline;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;

import java.util.logging.Logger;

/**
 * The composition root: builds the pipeline and its collaborators from configuration, once.
 *
 * <p>This is the only place that knows which implementation stands behind each abstraction. tinystruct
 * may create application instances per context, so the result is held statically and shared, instead
 * of being rebuilt (and its cache and counters reset) for every request.
 */
public final class TypesafeRuntime {

    private static final Logger LOGGER = Logger.getLogger(TypesafeRuntime.class.getName());
    private static volatile TypesafeRuntime shared;

    private final ActionSemanticRouter router;
    private final ConfirmationHandler confirmations;
    private final ConfirmedCallRunner confirmedCalls;
    private final DispatchMetrics metrics;

    public TypesafeRuntime(ActionSemanticRouter router, ConfirmationHandler confirmations,
                           ConfirmedCallRunner confirmedCalls, DispatchMetrics metrics) {
        this.router = router;
        this.confirmations = confirmations;
        this.confirmedCalls = confirmedCalls;
        this.metrics = metrics;
    }

    /** The shared runtime, built from {@code config} on first use. */
    public static TypesafeRuntime shared(Configuration<String> config) {
        TypesafeRuntime runtime = shared;
        if (runtime == null) {
            synchronized (TypesafeRuntime.class) {
                if (shared == null) shared = create(config);
                runtime = shared;
            }
        }
        return runtime;
    }

    /** Replaces the shared runtime; {@code null} makes the next {@link #shared} rebuild it. For tests. */
    public static void install(TypesafeRuntime runtime) {
        synchronized (TypesafeRuntime.class) {
            shared = runtime;
        }
    }

    public static TypesafeRuntime create(Configuration<String> config) {
        DispatchMetrics metrics = new DispatchMetrics();
        RoutingSettings settings = RoutingSettings.from(config);

        TypesafeClient client = decorate(buildClient(config), config, metrics);
        PrincipalResolver principals = instantiate(config, TypesafeConfig.PRINCIPAL_RESOLVER,
                PrincipalResolver.class, new DefaultPrincipalResolver());
        ConfirmationService confirmation = instantiate(config, TypesafeConfig.CONFIRMATION_SERVICE,
                ConfirmationService.class, null);
        if (confirmation != null) confirmation.configure(config);

        ArgumentValidator validator = new ArgumentValidator(settings.maxArgumentLength());
        ActionExecutor executor = new PathActionExecutor();

        DispatchPipeline pipeline = DispatchPipeline.of(client, new TokenSpanExtractor(),
                ConfidencePolicy.from(config), validator, executor, confirmation, principals, settings, metrics);
        ConfirmedCallRunner runner = new ConfirmedCallRunner(
                new ActionCatalog(settings.allowedActions()), validator, executor);
        return new TypesafeRuntime(pipeline, new ConfirmationHandler(confirmation, principals, metrics), runner, metrics);
    }

    public ActionSemanticRouter router() { return router; }
    public ConfirmationHandler confirmations() { return confirmations; }
    public ConfirmedCallRunner confirmedCalls() { return confirmedCalls; }
    public DispatchMetrics metrics() { return metrics; }

    // ---- wiring -----------------------------------------------------------------------------

    private static TypesafeClient buildClient(Configuration<String> config) {
        String apiKey = config.get(TypesafeConfig.API_KEY);
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("TYPESAFE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warning(TypesafeConfig.API_KEY + " (or TYPESAFE_API_KEY) is not set; routing will fail until it is.");
            return request -> {
                throw new ApplicationException("No TypeSafe API key configured. Set " + TypesafeConfig.API_KEY
                        + " or the TYPESAFE_API_KEY environment variable.");
            };
        }
        return new HttpTypesafeClient(
                config.get(TypesafeConfig.ENDPOINT), apiKey, config.get(TypesafeConfig.MODEL),
                TypesafeConfig.integer(config.get(TypesafeConfig.CONNECT_TIMEOUT_MS), 5000),
                TypesafeConfig.integer(config.get(TypesafeConfig.READ_TIMEOUT_MS), 30000),
                TypesafeConfig.integer(config.get(TypesafeConfig.RETRY_MAX), 3),
                TypesafeConfig.integer(config.get(TypesafeConfig.RETRY_BACKOFF_MS), 1000));
    }

    /** Metering sits inside the cache, so only real upstream calls are counted. */
    private static TypesafeClient decorate(TypesafeClient client, Configuration<String> config, DispatchMetrics metrics) {
        TypesafeClient metered = new MeteredTypesafeClient(client, metrics);
        RoutingCache cache = buildCache(config);
        return cache == null ? metered : new CachingTypesafeClient(metered, cache, metrics);
    }

    private static RoutingCache buildCache(Configuration<String> config) {
        String provider = config.get(TypesafeConfig.CACHE_PROVIDER);
        long ttl = TypesafeConfig.integer(config.get(TypesafeConfig.CACHE_TTL), 3600);
        return switch (provider == null || provider.isBlank() ? "memory" : provider.trim().toLowerCase()) {
            case "none" -> null;
            case "memory" -> new MemoryRoutingCache(ttl);
            case "redis" -> new RedisRoutingCache(config, ttl);
            default -> throw new ApplicationRuntimeException("Unknown " + TypesafeConfig.CACHE_PROVIDER + ": '"
                    + provider + "'. Valid values: none, memory, redis.");
        };
    }

    /**
     * Creates the implementation named by a class-name property (as tinystruct does for
     * {@code default.session.repository}). A configured class that cannot be created is a
     * misconfiguration and stops start-up rather than silently switching a safeguard off.
     */
    private static <T> T instantiate(Configuration<String> config, String key, Class<T> type, T fallback) {
        String className = config.get(key);
        if (className == null || className.isBlank()) return fallback;
        try {
            return type.cast(Class.forName(className.trim()).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new ApplicationRuntimeException("Cannot create " + key + " '" + className + "': " + e, e);
        }
    }
}
