package com.google.android.apps.common.testing.ui.espresso;

import java.util.concurrent.TimeUnit;

/**
 * Created by chris.sarbora on 2/2/14.
 */
public class WaitTimedOutException extends RuntimeException implements EspressoException {
    private final double mTimeout;

    public WaitTimedOutException(double timeout, Throwable throwable) {
        super(throwable);
        mTimeout = timeout;
    }

    @Override
    public String getMessage() {
        return String.format("Wait timed out after %f seconds: %s", mTimeout, getCause().getMessage());
    }
}
