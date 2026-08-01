package com.electrahub.ocpp.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class ObservabilityAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityAspect.class);
    private final MeterRegistry meterRegistry;

    /**
     * Executes observability aspect for `ObservabilityAspect`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.observability`.
     * @param meterRegistry input consumed by ObservabilityAspect.
     */
    public ObservabilityAspect(MeterRegistry meterRegistry) {
        LOGGER.info(" Entering ObservabilityAspect#ObservabilityAspect");
        LOGGER.debug(" Entering ObservabilityAspect#ObservabilityAspect with debug context");
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(public * com.electrahub..*.*(..)) && " +
            "(within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Controller *) || " +
            "within(@org.springframework.stereotype.Service *) || " +
            "within(@org.springframework.stereotype.Repository *) || " +
            "within(@org.springframework.stereotype.Component *)) && " +
            "!within(com.electrahub..observability..*)")
    /**
     * Executes observe for `ObservabilityAspect`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.observability`.
     * @param joinPoint input consumed by observe.
     * @return result produced by observe.
     */
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        long startedAt = System.nanoTime();

        Counter.builder("electrahub.method.invocations")
                .description("Total method invocations for Spring-managed application beans")
                .tag("class", className)
                .tag("method", methodName)
                .register(meterRegistry)
                .increment();

        // Per-method traces are valuable while diagnosing a single charger, but at
        // fleet scale they turn every meter update into several synchronous INFO
        // writes. Micrometer remains the production signal; detailed traces stay
        // available when this logger is enabled at DEBUG.
        LOGGER.debug("Starting {}.{}", className, methodName);

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = joinPoint.proceed();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            sample.stop(Timer.builder("electrahub.method.duration")
                    .description("Method execution duration for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", "success")
                    .register(meterRegistry));

            LOGGER.debug("Completed {}.{} in {} ms", className, methodName, durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            sample.stop(Timer.builder("electrahub.method.duration")
                    .description("Method execution duration for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", "failure")
                    .register(meterRegistry));

            Counter.builder("electrahub.method.failures")
                    .description("Total failed method invocations for Spring-managed application beans")
                    .tag("class", className)
                    .tag("method", methodName)
                    .register(meterRegistry)
                    .increment();

            LOGGER.warn("Failed {}.{} in {} ms: {}", className, methodName, durationMs, ex.toString());
            LOGGER.debug("Failure stack trace for {}.{}", className, methodName, ex);
            throw ex;
        }
    }

}
