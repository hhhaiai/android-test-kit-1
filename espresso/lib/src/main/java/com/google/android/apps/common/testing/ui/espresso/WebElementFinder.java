package com.google.android.apps.common.testing.ui.espresso;

public interface WebElementFinder {

  public WebElement getView() throws AmbiguousViewMatcherException, NoMatchingViewException;

}
