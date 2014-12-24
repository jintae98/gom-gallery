package com.gomdev.gallery;

import com.gomdev.gles.GLESObject;

/**
 * Created by gomdev on 14. 12. 18..
 */
public class GalleryObject extends GLESObject {
    static final String CLASS = "GalleryObject";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private int mPosition = -1;

    public GalleryObject() {
        super();
        init();
    }

    public GalleryObject(String name) {
        super(name);
        init();
    }

    private void init() {
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public int getPosition() {
        return mPosition;
    }
}