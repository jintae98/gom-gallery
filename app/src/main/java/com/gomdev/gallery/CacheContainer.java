package com.gomdev.gallery;

import android.graphics.drawable.BitmapDrawable;

/**
 * Created by gomdev on 14. 12. 17..
 */
public interface CacheContainer {
    public void setBitmapDrawable(BitmapDrawable drawable);

    public BitmapDrawable getBitmapDrawable();
}
