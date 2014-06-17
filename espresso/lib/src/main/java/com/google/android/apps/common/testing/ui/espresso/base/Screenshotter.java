package com.google.android.apps.common.testing.ui.espresso.base;

import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.google.android.apps.common.testing.ui.espresso.Root;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.inject.Inject;

/**
 * Created by chris.sarbora on 6/14/14.
 */
public class Screenshotter {
    private final RootsOracle rootsOracle;
    private final Looper mainLooper;

    @Inject
    public Screenshotter(RootsOracle rootsOracle,
                         Looper mainLooper) {
        this.rootsOracle = rootsOracle;
        this.mainLooper = mainLooper;
    }

    public Bitmap snap() {
        FutureTask<Bitmap> task = new FutureTask<Bitmap>(new Callable<Bitmap>() {
            @Override
            public Bitmap call() throws Exception {
                List<Root> roots = rootsOracle.get();
                if (roots.isEmpty()) {
                    throw new RuntimeException("No roots found.");
                }

                Display disp = ((WindowManager)roots.get(0).getDecorView().getContext().getSystemService(Service.WINDOW_SERVICE)).getDefaultDisplay();
                DisplayMetrics dm = new DisplayMetrics();
                disp.getMetrics(dm);
                Bitmap screenshot = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(screenshot);
                Paint p = new Paint();
                Rect container = new Rect(0, 0, dm.widthPixels, dm.heightPixels);
                Rect placement = new Rect();

                for (int i = roots.size() - 1; i >= 0; i--) {
                    Root r = roots.get(i);
                    View dv = r.getDecorView();
                    WindowManager.LayoutParams lp = r.getWindowLayoutParams().get();

                    dv.buildDrawingCache();
                    Bitmap cache = dv.getDrawingCache();
                    if (cache == null) // handle the case for WILL_NOT_CACHE_DRAWING
                        continue;
                    Gravity.apply(lp.gravity, cache.getWidth(), cache.getHeight(), container, placement);
                    placement.offset(lp.x, lp.y);
                    c.drawBitmap(cache, new Rect(0, 0, cache.getWidth(), cache.getHeight()), placement, p);
                    dv.destroyDrawingCache();
                }

                return screenshot;
            }
        });
        if (Looper.myLooper() == mainLooper) {
            task.run();
        } else {
            new Handler(mainLooper).post(task);
        }

        try {
            return task.get();
        } catch (Exception e) {
            throw new RuntimeException("Interrupted taking screenshot", e);
        }
    }

    public void snapToFile(File path) {
        Bitmap screenshot = snap();
        try {
            FileOutputStream fos = new FileOutputStream(path);
            try {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, fos);
            } finally {
                fos.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to write screenshot to %s", path), e);
        }
    }
}
