package com.google.android.apps.common.testing.ui.espresso.action;

import android.view.View;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by chris.sarbora on 2/7/14.
 */
public class InterpolatedLocation implements CoordinatesProvider {
    private final CoordinatesProvider mStart;
    private final CoordinatesProvider mEnd;
    private final float mFactor;

    public InterpolatedLocation(CoordinatesProvider start, CoordinatesProvider end, double percentAlong) {
        mStart = checkNotNull(start, "start must not be null");
        mEnd = checkNotNull(end, "end must not be null");
        checkArgument(percentAlong >= 0 && percentAlong <= 100, "percentAlong must be between 0 and 100");
        mFactor = (float)(percentAlong / 100.0);
    }

    @Override
    public float[] calculateCoordinates(View view) {
        float[] start = mStart.calculateCoordinates(view);
        float[] end = mEnd.calculateCoordinates(view);
        return new float[] {
                start[0] + (end[0] - start[0]) * mFactor,
                start[1] + (end[1] - start[1]) * mFactor
        };
    }
}
