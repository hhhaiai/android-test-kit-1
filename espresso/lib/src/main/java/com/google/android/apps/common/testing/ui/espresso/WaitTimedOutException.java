package com.google.android.apps.common.testing.ui.espresso;

import java.util.concurrent.TimeUnit;

/**
 * Created by chris.sarbora on 2/2/14.
 */
public class WaitTimedOutException extends RuntimeException implements EspressoException {
    private final double mTimeout;
    private final TimeUnit mTimeUnit;

    public WaitTimedOutException(double timeout, TimeUnit unit, Throwable throwable) {
        super(throwable);
        mTimeout = timeout;
        mTimeUnit = unit;
    }

    @Override
    public String getMessage() {
        return String.format("Wait timed out after %f %s: %s", mTimeout, mTimeUnit.toString(), getCause().getMessage());
    }
}
