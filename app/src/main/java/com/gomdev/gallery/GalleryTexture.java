package com.gomdev.gallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES20;

import com.gomdev.gles.GLESTexture;

/**
 * Created by gomdev on 14. 12. 17..
 */
class GalleryTexture implements BitmapContainer {
    static final String CLASS = "GalleryTexture";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    enum State {
        NONE,
        DECODING,
        QUEUING,
        LOADED
    }

    private GLESTexture mTexture = null;
    private GLESTexture.Builder mBuilder = null;
    private BitmapDrawable mDrawable = null;

    private State mState = State.NONE;

    private ImageLoadingListener mImageLoadingListener;

    volatile boolean mIsTextureLoadingFinished = false;
    volatile boolean mIsTextureLoadingStarted = false;
    private int mIndex = 0;

    GalleryTexture(int width, int height) {
        synchronized (this) {
            setState(State.NONE);
            mBuilder = new GLESTexture.Builder(GLES20.GL_TEXTURE_2D, width, height);
        }
    }

    GLESTexture getTexture() {
        return mTexture;
    }

    @Override
    public void setBitmapDrawable(BitmapDrawable drawable) {
        mDrawable = drawable;

        if (drawable instanceof AsyncDrawable) {
            synchronized (this) {
                setState(State.DECODING);
                mIsTextureLoadingStarted = true;
            }
            return;
        }

        if (mDrawable instanceof RecyclingBitmapDrawable) {
            ((RecyclingBitmapDrawable) mDrawable).setIsDisplayed(true);
        }

        if (mIsTextureLoadingFinished == false) {
            synchronized (this) {
                setState(State.QUEUING);
                mImageLoadingListener.onImageLoaded(mIndex, this);
            }
        }
        mIsTextureLoadingFinished = true;
    }

    @Override
    public BitmapDrawable getBitmapDrawable() {
        return mDrawable;
    }

    synchronized void load(Bitmap bitmap) {
        setState(State.LOADED);
        mTexture = mBuilder.load(bitmap);
    }

    void setIndex(int index) {
        mIndex = index;
    }

    int getIndex() {
        return mIndex;
    }

    void setImageLoadingListener(ImageLoadingListener listener) {
        mImageLoadingListener = listener;
    }

    boolean isTextureLoadingNeeded() {
        return (mIsTextureLoadingFinished == false) && (mIsTextureLoadingStarted == false);
    }

    boolean isOnTextureLoading() {
        return (mIsTextureLoadingFinished == false) && (mIsTextureLoadingStarted == true);
    }

    private void setState(State state) {
        mState = state;
    }

    synchronized State getState() {
        return mState;
    }
}
