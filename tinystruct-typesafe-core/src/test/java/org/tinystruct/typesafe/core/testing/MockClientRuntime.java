package org.tinystruct.typesafe.core.testing;

import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.TypesafeRuntime;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.config.RoutingSettings;
import org.tinystruct.typesafe.core.confirmation.ConfirmationHandler;
import org.tinystruct.typesafe.core.confirmation.ConfirmedCallRunner;
import org.tinystruct.typesafe.core.confirmation.DefaultPrincipalResolver;
import org.tinystruct.typesafe.core.confirmation.PrincipalResolver;
import org.tinystruct.typesafe.core.execution.ActionExecutor;
import org.tinystruct.typesafe.core.execution.PathActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.pipeline.DispatchPipeline;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.Set;

/** Installs a runtime whose TypeSafe client is a mock, so application-level entry points can be tested. */
public final class MockClientRuntime {

    private MockClientRuntime() {}

    public static void installReturning(String tool, String name, String role) {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, tool, 0.95);
        mock.addChoiceAnswer(tool + ".name", name, 0.95);
        mock.addChoiceAnswer(tool + ".role", role, 0.95);

        DispatchMetrics metrics = new DispatchMetrics();
        RoutingSettings settings = new RoutingSettings(Set.of("create-user"), Set.of(),
                RoutingSettings.Strategy.SINGLE, "jev-latest", false, 300, 200);
        ArgumentValidator validator = new ArgumentValidator();
        ActionExecutor executor = new PathActionExecutor();
        PrincipalResolver principals = new DefaultPrincipalResolver();

        DispatchPipeline pipeline = DispatchPipeline.of(mock, new TokenSpanExtractor(),
                new ConfidencePolicy(0.80, Double.NaN, 0.5), validator, executor, null, principals, settings, metrics);
        TypesafeRuntime.install(new TypesafeRuntime(pipeline,
                new ConfirmationHandler(null, principals, metrics),
                new ConfirmedCallRunner(new ActionCatalog(settings.allowedActions()), validator, executor),
                metrics));
    }
}
