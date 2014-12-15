package com.gomdev.gallery;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;

import com.gomdev.gles.GLESUtils;

public class ImageListFragment extends Fragment {
    static final String CLASS = "BucketListFragment";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private static Bitmap sLoadingBitmap = null;

    static {
        sLoadingBitmap = GLESUtils.makeBitmap(512, 512, Bitmap.Config.ARGB_8888, Color.BLACK);
    }

    private ImageManager mImageManager;
    private BucketInfo mBucketInfo;

    public ImageListFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView()");
        }

        View rootView = inflater.inflate(R.layout.fragment_main, container,
                false);

        mImageManager = ImageManager.getInstance();


        Activity activity = getActivity();

        int bucketPosition = getActivity().getIntent().getIntExtra(GalleryConfig.BUCKET_POSITION, 0);
        mBucketInfo = mImageManager.getBucketInfo(bucketPosition);

        Log.d(TAG, "onCreateView() bucket=" + mBucketInfo.getName());

        ImageGridAdapter adapter = new ImageGridAdapter(activity);

        GridView gridview = (GridView) rootView.findViewById(R.id.gridview);
        gridview.setAdapter(adapter);

        GalleryContext context = GalleryContext.getInstance();
        int columnWidth = context.getGridColumnWidth();

        gridview.setColumnWidth(columnWidth);
        adapter.setItemHeight(columnWidth);

        gridview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                Intent intent = new Intent(getActivity(), com.gomdev.gallery.ImageViewActivity.class);

                Log.d(TAG, "onItemClick() bucket position=" + mBucketInfo.getPosition() + " image position=" + (position - GalleryConfig.NUM_OF_COLUMNS));
                intent.putExtra(GalleryConfig.BUCKET_POSITION, mBucketInfo.getPosition());
                intent.putExtra(GalleryConfig.IMAGE_POSITION, position - GalleryConfig.NUM_OF_COLUMNS);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    // makeThumbnailScaleUpAnimation() looks kind of ugly here as the loading spinner may
                    // show plus the thumbnail image in GridView is cropped. so using
                    // makeScaleUpAnimation() instead.
                    ActivityOptions options =
                            ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight());
                    getActivity().startActivity(intent, options.toBundle());
                } else {
                    startActivity(intent);
                }
            }
        });

        return rootView;
    }

    @Override
    public void onResume() {
        if (DEBUG) {
            Log.d(TAG, "onResume()");
        }

        mImageManager.setLoadingBitmap(sLoadingBitmap);

        super.onResume();


    }

    public class ImageGridAdapter extends BaseAdapter {
        static final String CLASS = "ImageAdapter";
        static final String TAG = GalleryConfig.TAG + "_" + CLASS;
        static final boolean DEBUG = GalleryConfig.DEBUG;

        private final LayoutInflater mInflater;
        private int mNumOfImages = 0;
        private int mActionBarHeight = 0;

        private int mItemHeight = 0;
        private FrameLayout.LayoutParams mImageViewLayoutParams;

        public ImageGridAdapter(Context context) {
            mInflater = LayoutInflater.from(context);

            mNumOfImages = mBucketInfo.getNumOfImageInfos();

            mImageViewLayoutParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            // Calculate ActionBar height
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.actionBarSize, tv, true)) {
                mActionBarHeight = TypedValue.complexToDimensionPixelSize(
                        tv.data, context.getResources().getDisplayMetrics());
            }
        }

        @Override
        public int getCount() {
            return mNumOfImages + GalleryConfig.NUM_OF_COLUMNS;
        }

        @Override
        public Object getItem(int position) {
            return position < GalleryConfig.NUM_OF_COLUMNS ?
                    null : mBucketInfo.get(position - GalleryConfig.NUM_OF_COLUMNS);
        }

        @Override
        public long getItemId(int position) {
            return position < GalleryConfig.NUM_OF_COLUMNS ? 0 : position - GalleryConfig.NUM_OF_COLUMNS;
        }


        @Override
        public int getViewTypeCount() {
            // Two types of views, the normal ImageView and the top row of empty views
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return (position < GalleryConfig.NUM_OF_COLUMNS) ? 1 : 0;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout layout;

            if (position < GalleryConfig.NUM_OF_COLUMNS) {
                if (convertView == null) {
                    convertView = new ImageView(getActivity());
                }
                // Set empty view with height of ActionBar
                convertView.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, mActionBarHeight));
                return convertView;
            }

            if (convertView == null) {
                layout = (FrameLayout) mInflater.inflate(
                        R.layout.grid_item_image,
                        parent, false);
            } else {
                layout = (FrameLayout) convertView;

            }

            // Check the height matches our calculated column width
            if (layout.getLayoutParams().height != mItemHeight) {
                layout.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mItemHeight));
            }

            RecyclingImageView imageView = (RecyclingImageView) layout
                    .findViewById(R.id.image);
            imageView.setLayoutParams(mImageViewLayoutParams);
            ImageInfo imageInfo = mBucketInfo.get(position - GalleryConfig.NUM_OF_COLUMNS);
            mImageManager.loadThumbnail(imageInfo, imageView);

            return layout;
        }

        public void setItemHeight(int height) {
            if (height == mItemHeight) {
                return;
            }

            mItemHeight = height;
            mImageViewLayoutParams =
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mItemHeight);
            notifyDataSetChanged();

        }
    }
}