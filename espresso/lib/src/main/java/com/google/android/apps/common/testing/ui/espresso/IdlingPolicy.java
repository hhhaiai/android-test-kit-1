package com.google.android.apps.common.testing.ui.espresso;

import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows users to control idling idleTimeouts in Espresso.
 */
public final class IdlingPolicy {
  private static final String TAG = "IdlingPolicy";
  private enum ResponseAction { THROW_APP_NOT_IDLE, THROW_IDLE_TIMEOUT, LOG_ERROR };

  private final long idleTimeout;
  private final TimeUnit unit;
  private final ResponseAction errorHandler;
  private final boolean waitForAsyncTasks;

  /**
   * The amount of time the policy allows a resource to be non-idle.
   */
  public long getIdleTimeout(){
    return idleTimeout;
  }

  /**
   * The unit for {@linkgetIdleTimeout}.
   */
  public TimeUnit getIdleTimeoutUnit() {
    return unit;
  }

  /**
   * Whether UiController.loopUntilMainThreadIdle() should wait for AsyncTasks to be complete
   * @return
   */
  public boolean shouldWaitForAsyncTasks() { return waitForAsyncTasks; }


  /**
   * Invoked when the idle idleTimeout has been exceeded.
   *
   * @param busyResources the resources that are not idle.
   * @param message an additional message to include in an exception.
   */
  public void handleTimeout(List<String> busyResources, String message) {
    switch (errorHandler) {
      case THROW_APP_NOT_IDLE:
        throw AppNotIdleException.create(busyResources, message);
      case THROW_IDLE_TIMEOUT:
        throw new IdlingResourceTimeoutException(busyResources);
      case LOG_ERROR:
        Log.w(TAG, "These resources are not idle: " + busyResources);
        break;
      default:
        throw new IllegalStateException("should never reach here." + busyResources);
    }
  }

  Builder toBuilder() {
    return new Builder(this);
  }

  private IdlingPolicy(Builder builder) {
    checkArgument(builder.idleTimeout > 0);
    this.idleTimeout = builder.idleTimeout;
    this.unit = checkNotNull(builder.unit);
    this.errorHandler = checkNotNull(builder.errorHandler);
    this.waitForAsyncTasks = builder.waitForAsyncTasks;
  }

  static class Builder {
    private long idleTimeout = -1;
    private TimeUnit unit = null;
    private ResponseAction errorHandler = null;
    private boolean waitForAsyncTasks = true;

    public Builder() { }

    public IdlingPolicy build() {
      return new IdlingPolicy(this);
    }

    private Builder(IdlingPolicy copy) {
      this.idleTimeout = copy.idleTimeout;
      this.unit = copy.unit;
      this.errorHandler = copy.errorHandler;
      this.waitForAsyncTasks = copy.waitForAsyncTasks;
    }

    public Builder withIdlingTimeout(long idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    public Builder withIdlingTimeoutUnit(TimeUnit unit) {
      this.unit = unit;
      return this;
    }

    public Builder throwAppNotIdleException() {
      this.errorHandler = ResponseAction.THROW_APP_NOT_IDLE;
      return this;
    }

    public Builder throwIdlingResourceTimeoutException() {
      this.errorHandler = ResponseAction.THROW_IDLE_TIMEOUT;
      return this;
    }

    public Builder logWarning() {
      this.errorHandler = ResponseAction.LOG_ERROR;
      return this;
    }

    public Builder waitsForAsyncTasks(boolean wait) {
      this.waitForAsyncTasks = wait;
      return this;
    }
  }
}
