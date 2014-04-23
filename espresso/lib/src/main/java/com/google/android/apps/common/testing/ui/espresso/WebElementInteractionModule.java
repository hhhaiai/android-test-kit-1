package com.google.android.apps.common.testing.ui.espresso;

import org.hamcrest.Matcher;

import dagger.Module;
import dagger.Provides;

@Module(
    addsTo = GraphHolder.EspressoModule.class,
    injects = {
        WebElementInteraction.class
    }
)
class WebElementInteractionModule {

  private final Matcher<WebElement> webElementMatcher;

  WebElementInteractionModule(Matcher<WebElement> webElementMatcher) {
    this.webElementMatcher = webElementMatcher;
  }

  @Provides
  Matcher<WebElement> provideWebElementMathcer() {
    return webElementMatcher;
  }

  @Provides
  WebElementFinder provideWebElementFinder() {
    return null;
  }

}
