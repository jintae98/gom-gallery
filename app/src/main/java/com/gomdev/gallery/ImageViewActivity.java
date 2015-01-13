package com.gomdev.gallery;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

public class ImageViewActivity extends FragmentActivity implements View.OnClickListener {
    static final String CLASS = "ImageViewActivity";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private ImagePagerAdapter mAdapter;
    private ViewPager mPager;

    private BucketInfo mBucketInfo = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_detail_pager);

        final DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int bucketPosition = getIntent().getIntExtra(GalleryConfig.BUCKET_POSITION, 0);
        mBucketInfo = ImageManager.getInstance().getBucketInfo(bucketPosition);

        mAdapter = new ImagePagerAdapter(getSupportFragmentManager(), mBucketInfo.getNumOfImages());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);
        mPager.setOffscreenPageLimit(2);

        mPager.setOnPageChangeListener(mOnPageChangeListener);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final ActionBar actionBar = getActionBar();

            // Hide title text and set home as up
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);

            // Hide and show the ActionBar as the visibility changes
            mPager.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int vis) {
                            if ((vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
                                actionBar.hide();
                            } else {
//                                actionBar.show(); // FIX_ME
                            }
                        }
                    });

            mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            actionBar.hide();

            int imagePosition = getIntent().getIntExtra(GalleryConfig.IMAGE_POSITION, -1);
            if (imagePosition != -1) {
                mPager.setCurrentItem(imagePosition);
            }

        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onClick(View v) {
        final int vis = mPager.getSystemUiVisibility();
        if ((vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
            mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    private class ImagePagerAdapter extends FragmentStatePagerAdapter {
        private final int mSize;

        public ImagePagerAdapter(FragmentManager fm, int size) {
            super(fm);
            mSize = size;
        }

        @Override
        public int getCount() {
            return mSize;
        }

        @Override
        public Fragment getItem(int position) {
            return ImageViewFragment.newInstance(mBucketInfo.get(position));
        }
    }

    private ViewPager.OnPageChangeListener mOnPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int i, float v, int i2) {

        }

        @Override
        public void onPageSelected(int i) {
            SharedPreferences pref = ImageViewActivity.this.getSharedPreferences(GalleryConfig.PREF_NAME, 0);
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(GalleryConfig.PREF_IMAGE_INDEX, i);
            editor.putInt(GalleryConfig.PREF_BUCKET_INDEX, mBucketInfo.getPosition());
            editor.commit();
        }

        @Override
        public void onPageScrollStateChanged(int i) {

        }
    };
}
