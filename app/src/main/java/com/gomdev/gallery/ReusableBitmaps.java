package com.gomdev.gallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by gomdev on 15. 1. 13..
 */
public class ReusableBitmaps {
    static final String CLASS = "RecyclingBitmapDrawable";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private static ReusableBitmaps sReusableBitmaps = new ReusableBitmaps();

    private Set<SoftReference<Bitmap>> mReusableBitmaps;

    public static ReusableBitmaps getInstance() {
        return sReusableBitmaps;
    }

    private ReusableBitmaps() {
        mReusableBitmaps =
                Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
    }

    public void addBitmapToResuableSet(Bitmap bitmap) {
        synchronized (mReusableBitmaps) {
            mReusableBitmaps.add(new SoftReference<>(bitmap));
        }

        if (DEBUG) {
            Log.d(TAG, "addBitmapToResuableSet() add Bitmap width=" + bitmap.getWidth() + " height=" + bitmap.getHeight() + " size=" + mReusableBitmaps.size());
        }
    }

    // This method iterates through the reusable bitmaps, looking for one
    // to use for inBitmap:
    public Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
        Bitmap bitmap = null;

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized (mReusableBitmaps) {
                final Iterator<SoftReference<Bitmap>> iterator
                        = mReusableBitmaps.iterator();
                Bitmap item;

                while (iterator.hasNext()) {
                    item = iterator.next().get();

                    if (null != item && item.isMutable()) {
                        // Check to see it the item can be used for inBitmap.
                        if (canUseForInBitmap(item, options)) {
                            bitmap = item;

                            // Remove from reusable set so it can't be used again.
                            iterator.remove();
                            break;
                        }
                    } else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove();
                    }
                }
            }
        }

        return bitmap;
    }

    private static void addInBitmapOptions(BitmapFactory.Options options,
                                           ImageCache cache) {
        // inBitmap only works with mutable bitmaps, so force the decoder to
        // return mutable bitmaps.
        options.inMutable = true;

        if (cache != null) {
            // Try to find a bitmap to use for inBitmap.
            Bitmap inBitmap = ReusableBitmaps.getInstance().getBitmapFromReusableSet(options);

            if (inBitmap != null) {
                // If a suitable bitmap has been found, set it as the value of
                // inBitmap.
                options.inBitmap = inBitmap;
            }
        }
    }

    private static boolean canUseForInBitmap(
            Bitmap candidate, BitmapFactory.Options targetOptions) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // From Android 4.4 (KitKat) onward we can re-use if the byte size of
            // the new bitmap is smaller than the reusable bitmap candidate
            // allocation byte count.
            int width = targetOptions.outWidth / targetOptions.inSampleSize;
            int height = targetOptions.outHeight / targetOptions.inSampleSize;
            int byteCount = width * height * getBytesPerPixel(candidate.getConfig());

            return byteCount <= candidate.getAllocationByteCount();
        }

        // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
        return candidate.getWidth() == targetOptions.outWidth
                && candidate.getHeight() == targetOptions.outHeight
                && targetOptions.inSampleSize == 1;
    }

    private static int getBytesPerPixel(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        } else if (config == Bitmap.Config.RGB_565) {
            return 2;
        } else if (config == Bitmap.Config.ARGB_4444) {
            return 2;
        } else if (config == Bitmap.Config.ALPHA_8) {
            return 1;
        }
        return 1;
    }
}
