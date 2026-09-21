package org.tinystruct.typesafe.workflow;

import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.workflow.WorkflowDefinition;
import org.tinystruct.workflow.WorkflowEngine;
import org.tinystruct.workflow.repository.DatabaseSnapshotRepository;
import org.tinystruct.workflow.repository.FileSnapshotRepository;
import org.tinystruct.workflow.repository.MemorySnapshotRepository;
import org.tinystruct.workflow.repository.RedisSnapshotRepository;
import org.tinystruct.workflow.repository.SnapshotRepository;

import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Holds the single {@link WorkflowEngine} (and its {@link SnapshotRepository}) used by this module.
 *
 * <p>tinystruct may create application instances per context, and workflow variables are serialized
 * to JSON, so the engine cannot live in an instance field or a workflow variable. It is created once,
 * from the application's configuration.
 *
 * <p>The repository is kept next to the engine because the engine never deletes snapshots and
 * exposes no way to; a snapshot holds the call's arguments (possibly personal data), so removing it
 * once a call is finished is this module's job.
 */
public final class EngineHolder {

    private static final Logger LOGGER = Logger.getLogger(EngineHolder.class.getName());

    public static final String WORKFLOW_ID = "semantic-confirm";
    public static final String REPOSITORY = "typesafe.workflow.repository";
    public static final String SNAPSHOT_DIR = "typesafe.workflow.snapshot-dir";
    public static final String KEEP_COMPLETED = "typesafe.workflow.keep-completed";

    private static final Object LOCK = new Object();
    private static volatile WorkflowEngine engine;
    private static volatile SnapshotRepository repository;

    private EngineHolder() {}

    /**
     * The shared engine, created from {@code config} on first use. The {@code semantic-confirm}
     * definition is (re)registered on every call, which restores it after a restart because
     * definitions are held in memory.
     */
    public static WorkflowEngine getOrCreate(Configuration<String> config) {
        if (engine == null) {
            synchronized (LOCK) {
                if (engine == null) {
                    SnapshotRepository repo = buildRepository(config);
                    engine = new WorkflowEngine(repo);
                    repository = repo;
                }
            }
        }
        registerDefinition(engine);
        return engine;
    }

    /** The repository behind the shared engine. */
    public static SnapshotRepository repository() {
        return repository;
    }

    /** Registers the {@code semantic-confirm} definition on an engine. Idempotent. */
    public static void registerDefinition(WorkflowEngine target) {
        target.registerWorkflow(new WorkflowDefinition(WORKFLOW_ID)
                .addNode("semantic-confirm/await")
                .addNode("semantic-confirm/execute"));
    }

    private static SnapshotRepository buildRepository(Configuration<String> config) {
        String type = config.get(REPOSITORY);
        String dir = config.get(SNAPSHOT_DIR);
        return switch (type == null || type.isBlank() ? "memory" : type.trim().toLowerCase()) {
            case "memory" -> {
                LOGGER.warning("Using MemorySnapshotRepository: pending calls are lost on restart and not shared "
                        + "between processes. Use 'file', 'redis' or 'database' outside tests.");
                yield new MemorySnapshotRepository();
            }
            case "file" -> {
                String directory = dir == null || dir.isBlank() ? "workflow-snapshots/" : dir;
                LOGGER.info("Using FileSnapshotRepository at " + directory);
                yield new FileSnapshotRepository(Paths.get(directory));
            }
            // These two read their own settings (redis.host/port/password, database.*) from tinystruct.
            case "redis" -> new RedisSnapshotRepository();
            case "database" -> new DatabaseSnapshotRepository();
            default -> throw new IllegalArgumentException("Unknown " + REPOSITORY + ": '" + type
                    + "'. Valid values: memory, file, redis, database.");
        };
    }

    /** {@code true} if snapshots of finished calls should be kept (for audit) instead of deleted. */
    public static boolean keepCompleted(Configuration<String> config) {
        return TypesafeConfig.bool(config.get(KEEP_COMPLETED), false);
    }

    /** For tests: replaces the shared engine and repository, and registers the definition on the new engine. */
    static void reset(WorkflowEngine newEngine, SnapshotRepository newRepository) {
        synchronized (LOCK) {
            engine = newEngine;
            repository = newRepository;
            if (newEngine != null) registerDefinition(newEngine);
        }
    }
}
