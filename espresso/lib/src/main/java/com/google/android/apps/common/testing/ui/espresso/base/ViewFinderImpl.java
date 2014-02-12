package com.google.android.apps.common.testing.ui.espresso.base;

import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;

import com.google.android.apps.common.testing.ui.espresso.AmbiguousViewMatcherException;
import com.google.android.apps.common.testing.ui.espresso.NoMatchingViewException;
import com.google.android.apps.common.testing.ui.espresso.ViewFinder;
import com.google.android.apps.common.testing.ui.espresso.matcher.ViewMatchers;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import org.hamcrest.Matcher;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.android.apps.common.testing.ui.espresso.util.TreeIterables.breadthFirstViewTraversal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Implementation of {@link ViewFinder}.
 */
// TODO(user): in the future we may want to collect stats here about the size of the view
// hierarchy, average matcher execution time, warn when matchers take too long to execute, etc.
public final class ViewFinderImpl implements ViewFinder {

    private final Matcher<View> viewMatcher;
    private final Provider<List<View>> rootViewsProvider;

    @Inject
    ViewFinderImpl(Matcher<View> viewMatcher, Provider<List<View>> rootViewsProvider) {
        this.viewMatcher = viewMatcher;
        this.rootViewsProvider = rootViewsProvider;
    }

    @Override
    public View getView() throws AmbiguousViewMatcherException, NoMatchingViewException {
        checkMainThread();
        final Predicate<View> matcherPredicate = new MatcherPredicateAdapter<View>(
                checkNotNull(viewMatcher));

        View matchedView = null;
        List<View> roots = rootViewsProvider.get();
        Iterator<View> matchedViewIterator = Iterables.filter(
                Iterables.concat(Lists.transform(roots, new Function<View, Iterable<View>>() {
                    @Nullable @Override
                    public Iterable<View> apply(@Nullable View root) {
                        return breadthFirstViewTraversal(root);
                    }
                })), matcherPredicate).iterator();


        while (matchedViewIterator.hasNext()) {
            if (matchedView != null) {
                // Ambiguous!
                throw new AmbiguousViewMatcherException.Builder()
                        .withViewMatcher(viewMatcher)
                        .withRootViews(roots)
                        .withView1(matchedView)
                        .withView2(matchedViewIterator.next())
                        .withOtherAmbiguousViews(Iterators.toArray(matchedViewIterator, View.class))
                        .build();
            } else {
                matchedView = matchedViewIterator.next();
            }
        }
        if (null == matchedView) {
            final Predicate<View> adapterViewPredicate = new MatcherPredicateAdapter<View>(
                    ViewMatchers.isAssignableFrom(AdapterView.class));
            List<View> adapterViews = Lists.newArrayList(Iterables.concat(Lists.transform(roots, new Function<View, Iterable<View>>() {
                @Nullable @Override
                public Iterable<View> apply(@Nullable View root) {
                    return Iterables.filter(breadthFirstViewTraversal(root), adapterViewPredicate);
                }
            })));
            if (adapterViews.isEmpty()) {
                throw new NoMatchingViewException.Builder()
                        .withViewMatcher(viewMatcher)
                        .withRootView(roots)
                        .build();
            }

            String warning = String.format("\nIf the target view is not part of the view hierarchy, you "
                    + "may need to use Espresso.onData to load it from one of the following AdapterViews:%s"
                    , Joiner.on("\n- ").join(adapterViews));
            throw new NoMatchingViewException.Builder()
                    .withViewMatcher(viewMatcher)
                    .withRootView(roots)
                    .withAdapterViews(adapterViews)
                    .withAdapterViewWarning(Optional.of(warning))
                    .build();
        } else {
            return matchedView;
        }
    }

    private void checkMainThread() {
        checkState(Thread.currentThread().equals(Looper.getMainLooper().getThread()),
                "Executing a query on the view hierarchy outside of the main thread (on: %s)",
                Thread.currentThread().getName());
    }

    private static class MatcherPredicateAdapter<T> implements Predicate<T> {
        private final Matcher<? super T> matcher;

        private MatcherPredicateAdapter(Matcher<? super T> matcher) {
            this.matcher = checkNotNull(matcher);
        }

        @Override
        public boolean apply(T input) {
            return matcher.matches(input);
        }
    }
}
