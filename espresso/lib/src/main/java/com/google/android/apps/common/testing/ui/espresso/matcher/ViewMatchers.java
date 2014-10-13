package com.google.android.apps.common.testing.ui.espresso.matcher;

import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Checkable;
import android.widget.TextView;

import com.google.android.apps.common.testing.ui.espresso.util.HumanReadables;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import junit.framework.AssertionFailedError;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.google.android.apps.common.testing.ui.espresso.util.TreeIterables.breadthFirstViewTraversal;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hamcrest.Matchers.is;

/**
 * A collection of hamcrest matchers that match {@link View}s.
 */
public final class ViewMatchers {

    private ViewMatchers() {}

    /**
     * Returns a matcher that matches Views which are an instance of or subclass of the provided
     * class. Some versions of Hamcrest make the generic typing of this a nightmare, so we have a
     * special case for our users.
     */
    public static Matcher<View> isAssignableFrom(final Class<? extends View> clazz) {
        checkNotNull(clazz);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is assignable from class: " + clazz);
            }

            @Override
            public boolean matchesSafely(View view) {
                return clazz.isAssignableFrom(view.getClass());
            }
        };
    }

    /**
     * Returns a matcher that matches Views with class name matching the given matcher.
     */
    public static Matcher<View> withClassName(final Matcher<String> classNameMatcher) {
        checkNotNull(classNameMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("with class name: ");
                classNameMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return classNameMatcher.matches(view.getClass().getName());
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s that are currently displayed on the screen to the
     * user.
     *
     * Note: isDisplayed will select views that are partially displayed (eg: the full height/width of
     * the view is greater then the height/width of the visible rectangle). If you wish to ensure the
     * entire rectangle this view draws is displayed to the user use isCompletelyDisplayed.
     */
    public static Matcher<View> isDisplayed() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is displayed on the screen to the user");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.getGlobalVisibleRect(new Rect()) &&
                        withEffectiveVisibility(Visibility.VISIBLE).matches(view);
            }
        };
    }

    /**
     * Returns a matcher which only accepts a view whose height and width fit perfectly within
     * the currently displayed region of this view.
     *
     * There exist views (such as ScrollViews) whose height and width are larger then the physical
     * device screen by design. Such views will _never_ be completely displayed.
     */
    public static Matcher<View> isCompletelyDisplayed() {
        return isDisplayingAtLeast(100);
    }

    /**
     * Returns a matcher which accepts a view so long as a given percentage of that view's area is
     * not obscured by any other view and is thus visible to the user.
     *
     * @param areaPercentage an integer ranging from (0, 100] indicating how much percent of the
     *   surface area of the view must be shown to the user to be accepted.
     */
    public static Matcher<View> isDisplayingAtLeast(final int areaPercentage) {
        checkState(areaPercentage <= 100, "Cannot have over 100 percent: %s", areaPercentage);
        checkState(areaPercentage > 0, "Must have a positive, non-zero value: %s", areaPercentage);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(String.format(
                        "at least %s percent of the view's area is displayed to the user.", areaPercentage));
            }

            @Override
            public boolean matchesSafely(View view) {
                Rect visibleParts = new Rect();
                boolean visibleAtAll = view.getGlobalVisibleRect(visibleParts);
                if (!visibleAtAll) {
                    return false;
                }
                double maxArea = view.getHeight() * view.getWidth();
                ArrayList<Rect> visibleAreaRects = Lists.newArrayList(visibleParts);

                ArrayList<View> covers = getPotentialCovers(view);
                Rect vr = new Rect();
                ArrayList<Rect> scratch = new ArrayList<Rect>();
                for (View cover : covers) {
                    if (cover.getGlobalVisibleRect(vr)
                            && !withEffectiveVisibility(Visibility.GONE).matches(cover)) {
                        for (Rect visRect : visibleAreaRects) {
                            scratch.addAll(subtractRect(visRect, vr));
                        }
                        ArrayList<Rect> t = visibleAreaRects;
                        visibleAreaRects = scratch;
                        scratch = t;
                        scratch.clear();
                    }
                }

                double visibleArea = 0;
                for (Rect r : visibleAreaRects)
                    visibleArea += r.height() * r.width();

                int displayedPercentage = (int) ((visibleArea / maxArea) * 100);

                return displayedPercentage >= areaPercentage
                        && withEffectiveVisibility(Visibility.VISIBLE).matches(view);
            }

            /**
             * Returns a list of between 0 and 4 rectangles that represent the area covered by start, that is not covered by cutout.
             * @param start
             * @param cutout NOTE: this parameter gets modified. It will be clipped to be solely the intersection between it and start.
             * @return
             */
            private List<Rect> subtractRect(Rect start, Rect cutout) {
                if (!cutout.intersect(start))
                    return Collections.singletonList(start);

                // also serves as "4 sides incident" case
                if (cutout.contains(start))
                    return Collections.emptyList();

                // 3 sides incident
                if (start.right == cutout.right && start.bottom == cutout.bottom && start.left == cutout.left)                        // cutout is bottom
                    return Collections.singletonList(new Rect(start.left, start.top, start.right, cutout.top));                           // top
                if (start.bottom == cutout.bottom && start.left == cutout.left && start.top == cutout.top)                            // cutout is left
                    return Collections.singletonList(new Rect(cutout.right, start.top, start.right, start.bottom));                       // right
                if (start.left == cutout.left && start.top == cutout.top && start.right == cutout.right)                              // cutout is top
                    return Collections.singletonList(new Rect(start.left, cutout.bottom, start.right, start.bottom));                     // bottom
                if (start.top == cutout.top && start.right == cutout.right && start.bottom == cutout.bottom)                          // cutout is right
                    return Collections.singletonList(new Rect(start.left, start.top, cutout.left, start.bottom));                         // left

                // 2 sides incident, corner
                if (start.right == cutout.right && start.bottom == cutout.bottom)                                                     // bottom right corner
                    return Lists.newArrayList(new Rect(start.left, start.top, start.right, cutout.top),                                   // top
                            new Rect(start.left, cutout.top, cutout.left, start.bottom));                               // bottom left
                if (start.bottom == cutout.bottom && start.left == cutout.left)                                                       // bottom left corner
                    return Lists.newArrayList(new Rect(start.left, start.top, start.right, cutout.top),                                   // top
                            new Rect(cutout.right, cutout.top, start.right, start.bottom));                             // bottom right
                if (start.left == cutout.left && start.top == cutout.top)                                                             // top left
                    return Lists.newArrayList(new Rect(cutout.right, start.top, start.right, start.bottom),                               // right
                            new Rect(start.left, cutout.bottom, cutout.right, start.bottom));                           // bottom left
                if (start.top == cutout.top && start.right == cutout.right)                                                           // top right
                    return Lists.newArrayList(new Rect(start.left, start.top, cutout.left, cutout.bottom),                                // upper left
                            new Rect(start.left, cutout.bottom, start.right, start.bottom));                            // bottom
                // 2 sides incident, cleaving rect
                if (start.top == cutout.top && start.bottom == cutout.bottom)                                                         // cut vertically
                    return Lists.newArrayList(new Rect(start.left, start.top, cutout.left, start.bottom),                                 // left
                            new Rect(cutout.right, start.top, start.right, start.bottom));                              // right
                if (start.left == cutout.left && start.right == cutout.right)                                                         // cut horizontally
                    return Lists.newArrayList(new Rect(start.left, start.top, start.right, cutout.top),                                   // top
                            new Rect(start.left, cutout.bottom, start.right, start.bottom));                            // bottom

                // 1 side incident
                if (start.right == cutout.right)                                                                                      // right
                    return Lists.newArrayList(new Rect(start.left, start.top, cutout.left, start.bottom),                                 // left
                            new Rect(cutout.left, start.top, start.right, cutout.top),                                  // top right
                            new Rect(cutout.left, cutout.bottom, start.right, start.bottom));                           // bottom right
                if (start.bottom == cutout.bottom)                                                                                    // bottom
                    return Lists.newArrayList(new Rect(start.left, start.top, start.right, cutout.top),                                   // top
                            new Rect(start.left, cutout.top, cutout.left, start.bottom),                                // bottom left
                            new Rect(cutout.right, cutout.top, start.right, start.bottom));                             // bottom right
                if (start.left == cutout.left)                                                                                        // left
                    return Lists.newArrayList(new Rect(cutout.right, start.top, start.right, start.bottom),                               // right
                            new Rect(start.left, start.top, cutout.right, cutout.top),                                  // top left
                            new Rect(start.left, cutout.bottom, cutout.right, start.bottom));                           // bottom left
                if (start.top == cutout.top)                                                                                          // top
                    return Lists.newArrayList(new Rect(start.left, cutout.bottom, start.right, start.bottom),                             // bottom
                            new Rect(start.left, start.top, cutout.left, cutout.bottom),                                // top left
                            new Rect(cutout.right, start.top, start.right, cutout.bottom));                             // top right

                // 0 sides incident (cutout is in the middle of start, no sides touching)
                return Lists.newArrayList(new Rect(start.left, start.top, start.right, cutout.bottom),                                    // top
                        new Rect(start.left, cutout.top, cutout.left, cutout.bottom),                                   // middle left
                        new Rect(cutout.right, cutout.top, start.right, cutout.bottom),                                 // middle right
                        new Rect(start.left, cutout.bottom, start.right, start.bottom));                                // bottom
            }

            private ArrayList<View> getPotentialCovers(View v) {
                if (!(v.getParent() instanceof ViewGroup)) {
                    return new ArrayList<View>();
                }

                // add my uncles that could cover me
                ArrayList<View> list = getPotentialCovers(((View) v.getParent()));

                // add my siblings that could cover me
                ViewGroup parent = ((ViewGroup)v.getParent());
                boolean found = false;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (!found && v == parent.getChildAt(i)) {
                        found = true;
                        continue;
                    }

                    if (found)
                        list.add(parent.getChildAt(i));
                }

                return list;
            }
        };
    }



    /**
     * Returns a matcher that matches {@link View}s that are enabled.
     */
    public static Matcher<View> isEnabled() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is enabled");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.isEnabled();
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s that are focusable.
     */
    public static Matcher<View> isFocusable() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is focusable");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.isFocusable();
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s currently have focus.
     */
    public static Matcher<View> hasFocus() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has focus on the screen to the user");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.hasFocus();
            }
        };
    }

    /**
     * Returns an {@link Matcher} that matches {@link View}s based on their siblings.<br>
     * <br>
     * This may be particularly useful when a view cannot be uniquely selected on properties such as
     * text or R.id. For example: a call button is repeated several times in a contacts layout and the
     * only way to differentiate the call button view is by what appears next to it (e.g. the unique
     * name of the contact).
     *
     * @param siblingMatcher a {@link Matcher} for the sibling of the view.
     */
    public static Matcher<View> hasSibling(final Matcher<View> siblingMatcher) {
        checkNotNull(siblingMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has sibling: ");
                siblingMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                if (!(parent instanceof ViewGroup)) {
                    return false;
                }
                ViewGroup parentGroup = (ViewGroup) parent;
                for (int i = 0; i < parentGroup.getChildCount(); i++) {
                    if (siblingMatcher.matches(parentGroup.getChildAt(i))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Returns an {@link Matcher} that matches {@link View}s based on content description property
     * value. Sugar for withContentDescription(is("string")).
     *
     * @param text the text to match on.
     */
    public static Matcher<View> withContentDescription(String text) {
        return withContentDescription(is(text));
    }

    /**
     * Returns an {@link Matcher} that matches {@link View}s based on content description property
     * value.
     *
     * @param charSequenceMatcher a {@link CharSequence} {@link Matcher} for the content description
     */
    public static Matcher<View> withContentDescription(
            final Matcher<? extends CharSequence> charSequenceMatcher) {
        checkNotNull(charSequenceMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("with content description: ");
                charSequenceMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return charSequenceMatcher.matches(view.getContentDescription());
            }
        };
    }

    /**
     * Sugar for withId(is(int)).
     *
     * @param id the resource id.
     */
    public static Matcher<View> withId(int id) {
        return withId(is(id));
    }

    /**
     * Returns a matcher that matches {@link View}s based on resource ids. Note: Android resource ids
     * are not guaranteed to be unique. You may have to pair this matcher with another one to
     * guarantee a unique view selection.
     *
     * @param integerMatcher a Matcher for resource ids
     */
    public static Matcher<View> withId(final Matcher<Integer> integerMatcher) {
        checkNotNull(integerMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("with id: ");
                integerMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return integerMatcher.matches(view.getId());
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View} based on tag keys.
     *
     * @param key to match
     */
    public static Matcher<View> withTagKey(final int key) {
        return withTagKey(key, Matchers.<Object>notNullValue());
    }

    /**
     * Returns a matcher that matches {@link View}s based on tag keys.
     *
     * @param key to match
     * @param objectMatcher Object to match
     */
    public static Matcher<View> withTagKey(final int key, final Matcher<Object> objectMatcher) {
        checkNotNull(objectMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("with key: " + key);
                objectMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return objectMatcher.matches(view.getTag(key));
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s based on tag property values.
     *
     * @param tagValueMatcher a Matcher for the view's tag property value
     */
    public static Matcher<View> withTagValue(final Matcher<Object> tagValueMatcher) {
        checkNotNull(tagValueMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("with tag value: ");
                tagValueMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return tagValueMatcher.matches(view.getTag());
            }
        };
    }

    /**
     * Returns a matcher that matches {@link TextView} based on it's text property value. Note: View's
     * Sugar for withText(is("string")).
     */
    public static Matcher<View> withText(CharSequence text) {
        return withText(is(text));
    }

    /**
     * Returns a matcher that matches {@link TextView}s based on text property value. Note: View's
     * text property is never null. If you setText(null) it will still be "". Do not use null matcher.
     *
     * @param textMatcher {@link Matcher} of {@link CharSequence} with text to match
     */
    public static Matcher<View> withText(final Matcher<CharSequence> textMatcher) {
        checkNotNull(textMatcher);
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with text: ");
                textMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(TextView textView) {
                return textMatcher.matches(textView.getText());
            }
        };
    }

    public static Matcher<View> withHintText(CharSequence hint) {
        return withHintText(is(hint));
    }

    public static Matcher<View> withHintText(final Matcher<CharSequence> hintMatcher) {
        checkNotNull(hintMatcher);
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            protected boolean matchesSafely(TextView item) {
                return hintMatcher.matches(item.getHint());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("with text: ");
                hintMatcher.describeTo(description);
            }
        };
    }

    /**
     * Returns a matcher that matches a descendant of {@link TextView} that is displaying the string
     * associated with the given resource id.
     *
     * @param resourceId the string resource the text view is expected to hold.
     */
    public static Matcher<View> withText(final int resourceId) {

        return new BoundedMatcher<View, TextView>(TextView.class) {
            private String resourceName = null;
            private String expectedText = null;

            @Override
            public void describeTo(Description description) {
                description.appendText("with string from resource id: ");
                description.appendValue(resourceId);
                if (null != resourceName) {
                    description.appendText("[");
                    description.appendText(resourceName);
                    description.appendText("]");
                }
                if (null != expectedText) {
                    description.appendText(" value: ");
                    description.appendText(expectedText);
                }
            }

            @Override
            public boolean matchesSafely(TextView textView) {
                if (null == expectedText) {
                    try {
                        expectedText = textView.getResources().getString(resourceId);
                        resourceName = textView.getResources().getResourceEntryName(resourceId);
                    } catch (Resources.NotFoundException ignored) {
            /* view could be from a context unaware of the resource id. */
                    }
                }
                if (null != expectedText) {
                    return expectedText.equals(textView.getText());
                } else {
                    return false;
                }
            }
        };
    }

    /**
     * Returns a matcher that accepts if and only if the view is a CompoundButton (or subtype of) and
     * is in checked state.
     */
    public static Matcher<View> isChecked() {
        return withCheckBoxState(is(true));
    }

    /**
     * Returns a matcher that accepts if and only if the view is a CompoundButton (or subtype of) and
     * is not in checked state.
     */
    public static Matcher<View> isNotChecked() {
        return withCheckBoxState(is(false));
    }

    private static <E extends View & Checkable> Matcher<View> withCheckBoxState(
            final Matcher<Boolean> checkStateMatcher) {

        return new BoundedMatcher<View, E>(View.class, Checkable.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with checkbox state: ");
                checkStateMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(E checkable) {
                return checkStateMatcher.matches(checkable.isChecked());
            }
        };
    }

    /**
     * Returns an {@link Matcher} that matches {@link View}s with any content description.
     */
    public static Matcher<View> hasContentDescription() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has content description");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.getContentDescription() != null;
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s based on the presence of a descendant in its view
     * hierarchy.
     *
     * @param descendantMatcher the type of the descendant to match on
     */
    public static Matcher<View> hasDescendant(final Matcher<View> descendantMatcher) {
        checkNotNull(descendantMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has descendant: ");
                descendantMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(final View view) {
                final Predicate<View> matcherPredicate = new Predicate<View>() {
                    @Override
                    public boolean apply(View input) {
                        return input != view && descendantMatcher.matches(input);
                    }
                };

                Iterator<View> matchedViewIterator =
                        Iterables.filter(breadthFirstViewTraversal(view), matcherPredicate).iterator();

                return matchedViewIterator.hasNext();
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s that are clickable.
     */
    public static Matcher<View> isClickable() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is clickable");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.isClickable();
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s based on the given ancestor type.
     *
     * @param ancestorMatcher the type of the ancestor to match on
     */
    public static Matcher<View> isDescendantOfA(final Matcher<View> ancestorMatcher) {
        checkNotNull(ancestorMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is descendant of a: ");
                ancestorMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return checkAncestors(view.getParent(), ancestorMatcher);
            }

            private boolean checkAncestors(
                    ViewParent viewParent, Matcher<View> ancestorMatcher) {
                if (!(viewParent instanceof View)) {
                    return false;
                }
                if (ancestorMatcher.matches(viewParent)) {
                    return true;
                }
                return checkAncestors(viewParent.getParent(), ancestorMatcher);
            }
        };
    }

    /**
     * Returns a matcher that matches {@link View}s that have "effective" visibility set to the given
     * value. Effective visibility takes into account not only the view's visibility value, but also
     * that of its ancestors. In case of View.VISIBLE, this means that the view and all of its
     * ancestors have visibility=VISIBLE. In case of GONE and INVISIBLE, it's the opposite - any GONE
     * or INVISIBLE parent will make all of its children have their effective visibility.
     *
     * <p>
     * <p>
     * Note: Contrary to what the name may imply, view visibility does not directly translate into
     * whether the view is displayed on screen (use isDisplayed() for that). For example, the view and
     * all of its ancestors can have visibility=VISIBLE, but the view may need to be scrolled to in
     * order to be actually visible to the user. Unless you're specifically targeting the visibility
     * value with your test, use isDisplayed.
     */
    public static Matcher<View> withEffectiveVisibility(final Visibility visibility) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(
                        String.format("view has effective visibility=%s", visibility));
            }

            @Override
            public boolean matchesSafely(View view) {
                if (visibility.getValue() == View.VISIBLE) {
                    if (view.getVisibility() != visibility.getValue()) {
                        return false;
                    }
                    while (view.getParent() != null && view.getParent() instanceof View) {
                        view = (View) view.getParent();
                        if (view.getVisibility() != visibility.getValue()) {
                            return false;
                        }
                    }
                    return true;
                } else {
                    if (view.getVisibility() == visibility.getValue()) {
                        return true;
                    }
                    while (view.getParent() != null && view.getParent() instanceof View) {
                        view = (View) view.getParent();
                        if (view.getVisibility() == visibility.getValue()) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        };
    }

    /**
     * Enumerates the possible list of values for View.getVisibility().
     */
    public enum Visibility {
        VISIBLE(View.VISIBLE), INVISIBLE(View.INVISIBLE), GONE(View.GONE);

        private final int value;

        private Visibility(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * A matcher that accepts a view if and only if the view's parent is accepted by the provided
     * matcher.
     *
     * @param parentMatcher the matcher to apply on getParent.
     */
    public static Matcher<View> withParent(final Matcher<View> parentMatcher) {
        checkNotNull(parentMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has parent matching: ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                return parentMatcher.matches(view.getParent());
            }
        };
    }

    /**
     * A matcher that returns true if and only if the view's child is accepted by the provided
     * matcher.
     *
     * @param childMatcher the matcher to apply on the child views.
     */
    public static Matcher<View> withChild(final Matcher<View> childMatcher) {
        checkNotNull(childMatcher);
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has child: ");
                childMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                if (!(view instanceof ViewGroup)) {
                    return false;
                }

                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    if (childMatcher.matches(group.getChildAt(i))) {
                        return true;
                    }
                }

                return false;
            }
        };
    }


    /**
     * Returns a matcher that matches root {@link View}.
     */
    public static Matcher<View> isRoot() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("is a root view.");
            }

            @Override
            public boolean matchesSafely(View view) {
                return view.getRootView().equals(view);
            }
        };
    }

    /**
     * Returns a matcher that matches views that support input methods.
     */
    public static Matcher<View> supportsInputMethods() {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("supports input methods");
            }

            @Override
            public boolean matchesSafely(View view) {
                // At first glance, it would make sense to use view.onCheckIsTextEditor, but the android
                // javadoc is wishy-washy about whether authors are required to implement this method when
                // implementing onCreateInputConnection.
                return view.onCreateInputConnection(new EditorInfo()) != null;
            }
        };
    }

    /**
     * Returns a matcher that matches views that support input methods (e.g. EditText) and have the
     * specified IME action set in its {@link EditorInfo}.
     *
     * @param imeAction the IME action to match
     */
    public static Matcher<View> hasImeAction(int imeAction) {
        return hasImeAction(is(imeAction));
    }

    /**
     * Returns a matcher that matches views that support input methods (e.g. EditText) and have the
     * specified IME action set in its {@link EditorInfo}.
     *
     * @param imeActionMatcher a matcher for the IME action
     */
    public static Matcher<View> hasImeAction(final Matcher<Integer> imeActionMatcher) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has ime action: ");
                imeActionMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                EditorInfo editorInfo = new EditorInfo();
                InputConnection inputConnection = view.onCreateInputConnection(editorInfo);
                if (inputConnection == null) {
                    return false;
                }
                int actionId = editorInfo.actionId != 0 ? editorInfo.actionId
                        : editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
                return imeActionMatcher.matches(actionId);
            }
        };
    }

    /**
     * A replacement for MatcherAssert.assertThat that renders View objects nicely.
     *
     * @param actual the actual value.
     * @param matcher a matcher that accepts or rejects actual.
     */
    public static <T> void assertThat(T actual, Matcher<T> matcher) {
        assertThat("", actual, matcher);
    }

    /**
     * A replacement for MatcherAssert.assertThat that renders View objects nicely.
     *
     * @param message the message to display.
     * @param actual the actual value.
     * @param matcher a matcher that accepts or rejects actual.
     */
    public static <T> void assertThat(String message, T actual, Matcher<T> matcher) {
        if (!matcher.matches(actual)) {
            Description description = new StringDescription();
            description.appendText(message)
                    .appendText("\nExpected: ")
                    .appendDescriptionOf(matcher)
                    .appendText("\n     Got: ");
            if (actual instanceof View) {
                description.appendValue(HumanReadables.describe((View) actual));
            } else {
                description.appendValue(actual);
            }
            description.appendText("\n");
            throw new AssertionFailedError(description.toString());
        }
    }
}

