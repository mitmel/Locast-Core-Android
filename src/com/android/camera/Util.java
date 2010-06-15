/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import java.io.Closeable;
import java.io.IOException;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

/**
 * Collection of utility functions used in this package.
 */
public class Util {
    private static final String TAG = "Util";
    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_RIGHT = 1;
    public static final int DIRECTION_UP = 2;
    public static final int DIRECTION_DOWN = 3;

    private static OnClickListener sNullOnClickListener;

    private Util() {
    }

    // Rotates the bitmap by the specified degree.
    // If a new bitmap is created, the original bitmap is recycled.
    public static Bitmap rotate(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            final Matrix m = new Matrix();
            m.setRotate(degrees,
                    (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            try {
                final Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (final OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }
        return b;
    }

    // Whether we should recycle the input (unless the output is the input).
    public static final boolean RECYCLE_INPUT = true;
    public static final boolean NO_RECYCLE_INPUT = false;

    public static Bitmap transform(Matrix scaler,
                                   Bitmap source,
                                   int targetWidth,
                                   int targetHeight,
                                   boolean scaleUp,
                                   boolean recycle) {
        final int deltaX = source.getWidth() - targetWidth;
        final int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
             * In this case the bitmap is smaller, at least in one dimension,
             * than the target.  Transform it by placing as much of the image
             * as possible into the target and leaving the top/bottom or
             * left/right (or both) black.
             */
            final Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas c = new Canvas(b2);

            final int deltaXHalf = Math.max(0, deltaX / 2);
            final int deltaYHalf = Math.max(0, deltaY / 2);
            final Rect src = new Rect(
                    deltaXHalf,
                    deltaYHalf,
                    deltaXHalf + Math.min(targetWidth, source.getWidth()),
                    deltaYHalf + Math.min(targetHeight, source.getHeight()));
            final int dstX = (targetWidth  - src.width())  / 2;
            final int dstY = (targetHeight - src.height()) / 2;
            final Rect dst = new Rect(
                    dstX,
                    dstY,
                    targetWidth - dstX,
                    targetHeight - dstY);
            c.drawBitmap(source, src, dst, null);
            if (recycle) {
                source.recycle();
            }
            return b2;
        }
        final float bitmapWidthF = source.getWidth();
        final float bitmapHeightF = source.getHeight();

        final float bitmapAspect = bitmapWidthF / bitmapHeightF;
        final float viewAspect   = (float) targetWidth / targetHeight;

        if (bitmapAspect > viewAspect) {
            final float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        } else {
            final float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        }

        Bitmap b1;
        if (scaler != null) {
            // this is used for minithumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), scaler, true);
        } else {
            b1 = source;
        }

        if (recycle && b1 != source) {
            source.recycle();
        }

        final int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        final int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        final Bitmap b2 = Bitmap.createBitmap(
                b1,
                dx1 / 2,
                dy1 / 2,
                targetWidth,
                targetHeight);

        if (b2 != b1) {
            if (recycle || b1 != source) {
                b1.recycle();
            }
        }

        return b2;
    }

    public static <T>  int indexOf(T [] array, T s) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }

    public static void closeSilently(Closeable c) {
        if (c == null) {
			return;
		}
        try {
            c.close();
        } catch (final Throwable t) {
            // do nothing
        }
    }

    public static void closeSilently(ParcelFileDescriptor c) {
        if (c == null) {
			return;
		}
        try {
            c.close();
        } catch (final Throwable t) {
            // do nothing
        }
    }

    private static ParcelFileDescriptor makeInputStream(
            Uri uri, ContentResolver cr) {
        try {
            return cr.openFileDescriptor(uri, "r");
        } catch (final IOException ex) {
            return null;
        }
    }

    public static synchronized OnClickListener getNullOnClickListener() {
        if (sNullOnClickListener == null) {
            sNullOnClickListener = new OnClickListener() {
                public void onClick(View v) {
                }
            };
        }
        return sNullOnClickListener;
    }

    public static void Assert(boolean cond) {
        if (!cond) {
            throw new AssertionError();
        }
    }

    public static boolean equals(String a, String b) {
        // return true if both string are null or the content equals
        return a == b || a.equals(b);
    }

    public static Animation slideOut(View view, int to) {
        view.setVisibility(View.INVISIBLE);
        Animation anim;
        switch (to) {
            case DIRECTION_LEFT:
                anim = new TranslateAnimation(0, -view.getWidth(), 0, 0);
                break;
            case DIRECTION_RIGHT:
                anim = new TranslateAnimation(0, view.getWidth(), 0, 0);
                break;
            case DIRECTION_UP:
                anim = new TranslateAnimation(0, 0, 0, -view.getHeight());
                break;
            case DIRECTION_DOWN:
                anim = new TranslateAnimation(0, 0, 0, view.getHeight());
                break;
            default:
                throw new IllegalArgumentException(Integer.toString(to));
        }
        anim.setDuration(500);
        view.startAnimation(anim);
        return anim;
    }

    public static Animation slideIn(View view, int from) {
        view.setVisibility(View.VISIBLE);
        Animation anim;
        switch (from) {
            case DIRECTION_LEFT:
                anim = new TranslateAnimation(-view.getWidth(), 0, 0, 0);
                break;
            case DIRECTION_RIGHT:
                anim = new TranslateAnimation(view.getWidth(), 0, 0, 0);
                break;
            case DIRECTION_UP:
                anim = new TranslateAnimation(0, 0, -view.getHeight(), 0);
                break;
            case DIRECTION_DOWN:
                anim = new TranslateAnimation(0, 0, view.getHeight(), 0);
                break;
            default:
                throw new IllegalArgumentException(Integer.toString(from));
        }
        anim.setDuration(500);
        view.startAnimation(anim);
        return anim;
    }
}
