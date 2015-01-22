package com.gomdev.gallery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gomdev on 15. 1. 9..
 */
public class DateLabelInfo implements Serializable, GalleryInfo {
    static final String CLASS = "DateInfo";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private final int mPosition;
    private final String mDate;

    private int mWidth = 0;
    private int mHeight = 0;

    private int mNumOfRows = 0;
    private int mNumOfColumns = 0;

    private int mFirstImagePosition = 0;
    private int mLastImagePosition = 0;

    private List<ImageInfo> mImageInfos = new ArrayList<>();

    private BucketInfo mBucketInfo = null;

    public DateLabelInfo(int position, String date) {
        mPosition = position;
        mDate = date;
        mImageInfos.clear();
    }

    public void setBucketInfo(BucketInfo bucketInfo) {
        mBucketInfo = bucketInfo;
    }

    public BucketInfo getBucketInfo() {
        return mBucketInfo;
    }

    public int getPosition() {
        return mPosition;
    }

    public String getDate() {
        return mDate;
    }

    public void add(ImageInfo imageInfo) {
        mImageInfos.add(imageInfo);
        imageInfo.setDateLabelInfo(this);
    }

    public ImageInfo get(int position) {
        return mImageInfos.get(position);
    }

    public int getNumOfImages() {
        return mImageInfos.size();
    }

    public void setNumOfColumns(int numOfColumns) {
        if (mNumOfColumns != numOfColumns) {
            mNumOfRows = (int) Math.ceil((double) mImageInfos.size() / numOfColumns);
            mNumOfColumns = numOfColumns;
        }
    }

    public int getNumOfRows() {
        return mNumOfRows;
    }

    public void setFirstImagePosition(int position) {
        mFirstImagePosition = position;
    }

    public int getFirstImagePosition() {
        return mFirstImagePosition;
    }

    public void setLastImagePosition(int position) {
        mLastImagePosition = position;
    }

    public int getLastImagePosition() {
        return mLastImagePosition;
    }
}
