package com.google.android.apps.common.testing.ui.espresso;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;

import com.google.android.apps.common.testing.ui.espresso.action.ScrollToAction;
import com.google.android.apps.common.testing.ui.espresso.base.MainThread;
import com.google.android.apps.common.testing.ui.espresso.matcher.RootMatchers;
import com.google.android.apps.common.testing.ui.espresso.util.HumanReadables;
import com.google.common.base.Optional;

import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.android.apps.common.testing.ui.espresso.matcher.ViewMatchers.isAssignableFrom;
import static com.google.android.apps.common.testing.ui.espresso.matcher.ViewMatchers.isDescendantOfA;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides the primary interface for test authors to perform actions or asserts on views.
 * <p>
 * Each interaction is associated with a view identified by a view matcher. All view actions and
 * asserts are performed on the UI thread (thus ensuring sequential execution). The same goes for
 * retrieval of views (this is done to ensure that view state is "fresh" prior to execution of each
 * operation).
 * <p>
 */
public final class ViewInteraction {

    private static final String TAG = ViewInteraction.class.getSimpleName();

    private final UiController uiController;
    private final ViewFinder viewFinder;
    private final Executor mainThreadExecutor;
    private final FailureHandler failureHandler;
    private final Matcher<View> viewMatcher;
    private final AtomicReference<Matcher<Root>> rootMatcherRef;
    private final Provider<List<Root>> rootsOracle;

    @Inject
    ViewInteraction(
            UiController uiController,
            ViewFinder viewFinder,
            @MainThread Executor mainThreadExecutor,
            FailureHandler failureHandler,
            Matcher<View> viewMatcher,
            AtomicReference<Matcher<Root>> rootMatcherRef,
            Provider<List<Root>> rootsOracle) {
        this.viewFinder = checkNotNull(viewFinder);
        this.uiController = checkNotNull(uiController);
        this.failureHandler = checkNotNull(failureHandler);
        this.mainThreadExecutor = checkNotNull(mainThreadExecutor);
        this.viewMatcher = checkNotNull(viewMatcher);
        this.rootMatcherRef = checkNotNull(rootMatcherRef);
        this.rootsOracle = checkNotNull(rootsOracle);
    }

    /**
     * Performs the given action(s) on the view selected by the current view matcher. If more than one
     * action is provided, actions are executed in the order provided with precondition checks running
     * prior to each action.
     *
     * @param viewActions one or more actions to execute.
     * @return this interaction for further perform/verification calls.
     */
    public ViewInteraction perform(final ViewAction... viewActions) {
        checkNotNull(viewActions);
        for (ViewAction action : viewActions) {
            doPerform(action);
        }
        return this;
    }


    /**
     * Makes this ViewInteraction scoped to the root selected by the given root matcher.
     */
    public ViewInteraction inRoot(Matcher<Root> rootMatcher) {
        this.rootMatcherRef.set(checkNotNull(rootMatcher));
        return this;
    }

    public ViewInteraction inRoots(Matcher<Root> rootMatcher) {
        this.rootMatcherRef.set(new RootMatchers.MultiRootMatcher(rootMatcher));
        return this;
    }

    private void doPerform(final ViewAction viewAction) {
        checkNotNull(viewAction);
        final Matcher<? extends View> constraints = checkNotNull(viewAction.getConstraints());
        runSynchronouslyOnUiThread(new Runnable() {

            @Override
            public void run() {
                uiController.loopMainThreadUntilIdle();
                View targetView = viewFinder.getView();
                Log.i(TAG, String.format(
                        "Performing '%s' action on view %s", viewAction.getDescription(), viewMatcher));
                if (!constraints.matches(targetView)) {
                    // TODO(user): update this to describeMismatch once hamcrest is updated to new
                    StringDescription stringDescription = new StringDescription(new StringBuilder(
                            "Action will not be performed because the target view "
                                    + "does not match one or more of the following constraints:\n"));
                    constraints.describeTo(stringDescription);
                    stringDescription.appendText("\nTarget view: ")
                            .appendValue(HumanReadables.describe(targetView));

                    if (viewAction instanceof ScrollToAction
                            && isDescendantOfA(isAssignableFrom((AdapterView.class))).matches(targetView)) {
                        stringDescription.appendText(
                                "\nFurther Info: ScrollToAction on a view inside an AdapterView will not work. "
                                        + "Use Espresso.onData to load the view.");
                    }
                    throw new PerformException.Builder()
                            .withActionDescription(viewAction.getDescription())
                            .withViewDescription(viewMatcher.toString())
                            .withCause(new RuntimeException(stringDescription.toString()))
                            .build();
                } else {
                    viewAction.perform(uiController, targetView);
                }
            }
        });
    }

    /**
     * Checks the given {@link ViewAssertion} on the the view selected by the current view matcher.
     *
     * @param viewAssert the assertion to perform.
     * @return this interaction for further perform/verification calls.
     */
    public ViewInteraction check(final ViewAssertion viewAssert) {
        checkNotNull(viewAssert);
        runSynchronouslyOnUiThread(new Runnable() {
            @Override
            public void run() {
                uiController.loopMainThreadUntilIdle();

                Optional<View> targetView = Optional.absent();
                Optional<NoMatchingViewException> missingViewException = Optional.absent();
                try {
                    targetView = Optional.of(viewFinder.getView());
                } catch (NoMatchingViewException nsve) {
                    missingViewException = Optional.of(nsve);
                }
                viewAssert.check(targetView, missingViewException);
            }
        });
        return this;
    }

    public ViewInteraction waitFor(long timeout, TimeUnit unit, final ViewAssertion viewAssert) {
        checkNotNull(viewAssert);

        final HashSet<ViewTreeObserver> observers = new HashSet<ViewTreeObserver>();
        final Object notifier = new Object();
        final ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                synchronized (notifier) {
                    notifier.notifyAll();
                }
            }
        };
//      final ViewTreeObserver.OnWindowFocusChangeListener windowListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
//          @Override
//          public void onWindowFocusChanged(boolean hasFocus) {
//              synchronized (notifier) {
//                  ViewTreeObserver vto = rootViewPicker.get().getViewTreeObserver();
//                  vto.addOnGlobalLayoutListener(layoutListener);
//                  vto.addOnWindowFocusChangeListener(this);
//                  observers.add(vto);
//                  notifier.notifyAll();
//              }
//          }
//      };

        runSynchronouslyOnUiThread(new Runnable() {
            @Override
            public void run() {
                // TODO: how can we find roots that are added *during* the wait?
                // TODO: also, we should probably use a rootViewsProvider here now
                for (Root r : rootsOracle.get()) {
                    ViewTreeObserver vto = r.getDecorView().getViewTreeObserver();
                    vto.addOnGlobalLayoutListener(layoutListener);
//                  vto.addOnWindowFocusChangeListener(windowListener);
                    observers.add(vto);
                }
            }
        });

        final Throwable t[] = new Throwable[1];
        try {
            long target = SystemClock.elapsedRealtime() + TimeUnit.MILLISECONDS.convert(timeout, unit);
            while (true) {
                runSynchronouslyOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            viewAssert.check(Optional.of(viewFinder.getView()), Optional.<NoMatchingViewException>absent());
                            t[0] = null;
                        } catch (NoMatchingViewException nmve) {
                            t[0] = nmve;
                        } catch (AssertionError ae) {
                            t[0] = ae;
                        }
                    }
                });

                if (t[0] == null || SystemClock.elapsedRealtime() >= target)
                    break;

                try {
                    synchronized (notifier) {
                        notifier.wait(Math.max(0, target - SystemClock.elapsedRealtime()));
                    }
                } catch (InterruptedException e) { /* go round again */ }
            }
        } finally {
            for (ViewTreeObserver vto : observers) {
                if (!vto.isAlive())
                    continue;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                    vto.removeGlobalOnLayoutListener(layoutListener);
                else
                    vto.removeOnGlobalLayoutListener(layoutListener);
//              vto.removeOnWindowFocusChangeListener(windowListener);
            }
        }

        if (t[0] != null)
            throw new WaitTimedOutException(timeout, unit, t[0]);

        return this;
    }

    private void runSynchronouslyOnUiThread(Runnable action) {
        FutureTask<Void> uiTask = new FutureTask<Void>(action, null);
        mainThreadExecutor.execute(uiTask);
        try {
            uiTask.get();
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted  running UI task", ie);
        } catch (ExecutionException ee) {
            failureHandler.handle(ee.getCause(), viewMatcher);
        }
    }
}
