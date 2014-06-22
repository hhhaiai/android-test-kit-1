package com.google.android.apps.common.testing.ui.espresso;

import java.util.concurrent.TimeUnit;

import static com.google.android.apps.common.testing.ui.espresso.assertion.ViewAssertions.matches;
import static com.google.android.apps.common.testing.ui.espresso.matcher.ViewMatchers.isDisplayed;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows users fine grain control over idling policies.
 *
 * Espresso's default idling policies are suitable for most usecases - however
 * certain execution environments (like the ARM emulator) might be very slow.
 * This class allows users the ability to adjust defaults to sensible values
 * for their environments.
 */
public final class IdlingPolicies {

    private IdlingPolicies() { }

    private static volatile IdlingPolicy masterIdlingPolicy = new IdlingPolicy.Builder()
            .withIdlingTimeout(60)
            .withIdlingTimeoutUnit(TimeUnit.SECONDS)
            .throwAppNotIdleException()
            .build();


    private static volatile IdlingPolicy dynamicIdlingResourceErrorPolicy = new IdlingPolicy.Builder()
            .withIdlingTimeout(26)
            .withIdlingTimeoutUnit(TimeUnit.SECONDS)
            .throwIdlingResourceTimeoutException()
            .build();

    private static volatile IdlingPolicy dynamicIdlingResourceWarningPolicy =
            new IdlingPolicy.Builder()
                    .withIdlingTimeout(5)
                    .withIdlingTimeoutUnit(TimeUnit.SECONDS)
                    .logWarning()
                    .build();

    private static volatile double viewCheckTimeout = 0;

    private static volatile ViewAssertion viewPerformPrecondition = matches(isDisplayed());

    /**
     * Updates the IdlingPolicy used in UiController.loopUntil to detect AppNotIdleExceptions.
     *
     * @param timeout the timeout before an AppNotIdleException is created.
     * @param unit the unit of the timeout value.
     */
    public static void setMasterPolicyTimeout(long timeout, TimeUnit unit) {
        checkArgument(timeout > 0);
        checkNotNull(unit);
        masterIdlingPolicy = masterIdlingPolicy.toBuilder()
                .withIdlingTimeout(timeout)
                .withIdlingTimeoutUnit(unit)
                .build();
    }

    /**
     * Updates the IdlingPolicy used by IdlingResourceRegistry to determine when IdlingResources
     * timeout.
     *
     * @param timeout the timeout before an IdlingResourceTimeoutException is created.
     * @param unit the unit of the timeout value.
     */
    public static void setIdlingResourceTimeout(long timeout, TimeUnit unit) {
        checkArgument(timeout > 0);
        checkNotNull(unit);
        dynamicIdlingResourceErrorPolicy = dynamicIdlingResourceErrorPolicy.toBuilder()
                .withIdlingTimeout(timeout)
                .withIdlingTimeoutUnit(unit)
                .build();
    }

    /**
     * This seems dumb to have so much abstraction and Builders and such here.
     * @param wait
     */
    public static void setWaitForAsyncTasksPolicy(boolean wait) {
        masterIdlingPolicy = masterIdlingPolicy.toBuilder()
                .waitsForAsyncTasks(wait)
                .build();
    }

    public static double getViewCheckTimeout() {
        return viewCheckTimeout;
    }

    public static void setViewCheckTimeout(double timeout) {
        viewCheckTimeout = timeout;
    }

    public static ViewAssertion getViewPerformPrecondition() {
        return viewPerformPrecondition;
    }

    public static void setViewPerformPrecondition(ViewAssertion viewPerformPrecondition) {
        IdlingPolicies.viewPerformPrecondition = viewPerformPrecondition;
    }

    public static IdlingPolicy getMasterIdlingPolicy() {
        return masterIdlingPolicy;
    }

    public static IdlingPolicy getDynamicIdlingResourceWarningPolicy() {
        return dynamicIdlingResourceWarningPolicy;
    }

    public static IdlingPolicy getDynamicIdlingResourceErrorPolicy() {
        return dynamicIdlingResourceErrorPolicy;
    }
}
