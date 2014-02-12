package com.google.android.apps.common.testing.ui.espresso;

import android.view.View;

import com.google.android.apps.common.testing.ui.espresso.base.RootViewPicker;
import com.google.android.apps.common.testing.ui.espresso.base.ViewFinderImpl;
import com.google.android.apps.common.testing.ui.espresso.matcher.RootMatchers;

import org.hamcrest.Matcher;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dagger.Module;
import dagger.Provides;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Adds the user interaction scope to the Espresso graph.
 */
@Module(
    addsTo = GraphHolder.EspressoModule.class,
    injects = {ViewInteraction.class})
class ViewInteractionModule {

  private final Matcher<View> viewMatcher;
  private final AtomicReference<Matcher<Root>> rootMatcher =
      new AtomicReference<Matcher<Root>>(RootMatchers.DEFAULT);

  ViewInteractionModule(Matcher<View> viewMatcher) {
    this.viewMatcher = checkNotNull(viewMatcher);
  }

  @Provides
  AtomicReference<Matcher<Root>> provideRootMatcher() {
    return rootMatcher;
  }

  @Provides
  Matcher<View> provideViewMatcher() {
    return viewMatcher;
  }

  @Provides
  ViewFinder provideViewFinder(ViewFinderImpl impl) {
    return impl;
  }

  @Provides
  public List<View> provideRootView(RootViewPicker rootViewPicker) {
    // RootsOracle acts as a provider, but returning Providers is illegal, so delegate.
    return rootViewPicker.get();
  }
}
