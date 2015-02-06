package com.gomdev.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLES20;
import android.util.SparseArray;

import com.gomdev.gles.GLESAnimator;
import com.gomdev.gles.GLESAnimatorCallback;
import com.gomdev.gles.GLESCamera;
import com.gomdev.gles.GLESGLState;
import com.gomdev.gles.GLESNode;
import com.gomdev.gles.GLESObject;
import com.gomdev.gles.GLESObjectListener;
import com.gomdev.gles.GLESShader;
import com.gomdev.gles.GLESTexture;
import com.gomdev.gles.GLESTransform;
import com.gomdev.gles.GLESUtils;
import com.gomdev.gles.GLESVector3;
import com.gomdev.gles.GLESVertexInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by gomdev on 15. 1. 12..
 */

public class GalleryObjects implements ImageLoadingListener, GridInfoChangeListener {
    static final String CLASS = "GalleryObjects";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private final Context mContext;
    private final ImageListRenderer mRenderer;
    private final ReusableBitmaps mReusableBitmaps;

    private GallerySurfaceView mSurfaceView = null;

    private List<DateLabelObject> mDateLabelObjects = new LinkedList<>();
    private HashMap<DateLabelObject, ImageObjects> mObjectsMap = new HashMap<>();

    private GLESGLState mDateLabelGLState = null;
    private GLESGLState mImageGLState = null;
    private GLESShader mDateLabelShader = null;
    private GLESShader mImageShader = null;

    private GridInfo mGridInfo = null;
    private BucketInfo mBucketInfo = null;
    private int mNumOfDateInfos = 0;
    private int mDateLabelHeight = 0;
    private int mSpacing = 0;
    private int mColumnWidth = 0;
    private int mPrevColumnWidth = 0;
    private int mNumOfColumns = 0;
    private int mPrevNumOfColumns = 0;
    private int mActionBarHeight = 0;

    private Bitmap mLoadingBitmap = null;
    private GLESTexture mDummyDateLabelTexture = null;
    private GLESTexture mDummyImageTexture = null;

    private int mWidth = 0;
    private int mHeight = 0;

    private List<TextureMappingInfo> mTextureMappingInfos = new LinkedList<>();
    private Queue<GalleryTexture> mWaitingTextures = new ConcurrentLinkedQueue<>();
    private SparseArray<DateLabelObject> mInvisibleObjects = new SparseArray<>();
    private ArrayList<DateLabelObject> mAnimationObjects = new ArrayList<>();

    private List<GLESAnimator> mAnimators = new ArrayList<>();
    private int mAnimationFinishCount = 0;
    private float mAlpha = 1.0f;

    public GalleryObjects(Context context, ImageListRenderer renderer) {
        mContext = context;
        mRenderer = renderer;
        mReusableBitmaps = ReusableBitmaps.getInstance();

        clear();
    }

    public void clear() {
        mDateLabelObjects.clear();
        mObjectsMap.clear();
        mTextureMappingInfos.clear();
        mWaitingTextures.clear();
        mAnimationObjects.clear();
    }

    // Rendering

    public void update(boolean needToMapTexture) {
        if (needToMapTexture == true) {
            updateTexture();
        }

        checkVisibility();

        update();
    }

    // this function should be called on GLThread
    public void updateTexture() {
        GalleryTexture texture = mWaitingTextures.poll();

        if (texture != null) {
            TextureMappingInfo textureMappingInfo = mTextureMappingInfos.get(texture.getPosition());
            final GalleryObject object = textureMappingInfo.getObject();

            final Bitmap bitmap = texture.getBitmapDrawable().getBitmap();
            texture.load(bitmap);

            mReusableBitmaps.addBitmapToResuableSet(bitmap);

            object.setTexture(texture.getTexture());
        }

        if (mWaitingTextures.size() > 0) {
            mSurfaceView.requestRender();
        }

        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject dateLabelObject = iter.next();

            ImageObjects imageObjects = mObjectsMap.get(dateLabelObject);
            imageObjects.updateTexture();
        }
    }

    public void checkVisibility() {
        float translateY = mGridInfo.getTranslateY();
        float viewportTop = mHeight * 0.5f - translateY;
        float viewportBottom = viewportTop - mHeight;

        int index = 0;
        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            GalleryNode parentNode = object.getParentNode();

            if (mInvisibleObjects.get(index) != null) {
                parentNode.setVisibility(false);

                if (parentNode.isVisibilityChanged() == true) {
                    unmapTexture(index, object);
                    object.setTextureMapping(false);
                }
                index++;
                continue;
            }

            ImageObjects imageObjects = mObjectsMap.get(object);

            float top = object.getTop();
            float bottom = imageObjects.getBottom();

            if (bottom < viewportTop && top > viewportBottom) {
                parentNode.setVisibility(true);

                if (parentNode.isVisibilityChanged() == true) {
                    if (object.isTexturMapped() == false) {
                        mapTexture(index);
                        object.setTextureMapping(true);
                    }
                }

                imageObjects.checkVisibility(true);
            } else {
                parentNode.setVisibility(false);

                if (parentNode.isVisibilityChanged() == true) {
                    unmapTexture(index, object);
                    object.setTextureMapping(false);
                }

                imageObjects.checkVisibility(false);
            }

            index++;
        }
    }

    private void update() {
        int requestRenderCount = 0;
        int size = mAnimators.size();
        for (int i = 0; i < size; i++) {
            GLESAnimator animator = mAnimators.get(i);
            if (animator.doAnimation() == true) {
                requestRenderCount++;
            }
        }

        if (requestRenderCount > 0) {
            mSurfaceView.requestRender();
        }

        size = mAnimationObjects.size();
        for (int i = 0; i < size; i++) {
            DateLabelObject object = mAnimationObjects.get(i);
            ImageObjects imageObjects = mObjectsMap.get(object);
            imageObjects.update();
        }
    }

    public void mapTexture(int index) {
        TextureMappingInfo textureMappingInfo = mTextureMappingInfos.get(index);
        DateLabelInfo dateLabelInfo = (DateLabelInfo) textureMappingInfo.getGalleryInfo();

        GalleryTexture texture = textureMappingInfo.getTexture();
        if (texture == null) {
            texture = new GalleryTexture(mWidth, mHeight);
            texture.setPosition(index);
            texture.setImageLoadingListener(this);
        }

        if ((texture != null && texture.isTextureLoadingNeeded() == true)) {
            makeDateLabel(dateLabelInfo, texture);
            textureMappingInfo.set(texture);
            mSurfaceView.requestRender();
        }
    }

    public void unmapTexture(int index, GalleryObject object) {
        object.setTexture(mDummyDateLabelTexture);

        TextureMappingInfo textureMappingInfo = mTextureMappingInfos.get(index);
        GalleryTexture texture = textureMappingInfo.getTexture();
        BitmapWorker.cancelWork(texture);
        mWaitingTextures.remove(texture);

        textureMappingInfo.set(null);
    }

    // onSurfaceChanged

    public void onSurfaceChanged(int width, int height) {
        mWidth = width;
        mHeight = height;

        mLoadingBitmap = GLESUtils.makeBitmap(16, 16, Bitmap.Config.ARGB_8888, Color.WHITE);

        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();

            ImageObjects imageObjects = mObjectsMap.get(object);
            imageObjects.onSurfaceChanged(width, height);
        }
    }

    public void setupObjects(GLESCamera camera) {
        float yOffset = mHeight * 0.5f - mActionBarHeight;

        int index = 0;
        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();

            object.setCamera(camera);

            float left = mSpacing - mWidth * 0.5f;
            float top = yOffset;
            float width = mWidth - mSpacing * 2f;
            float height = mDateLabelHeight;

            object.setLeftTop(left, top);
            object.setTranslate(left + width * 0.5f, top - height * 0.5f);

            float[] vertex = GLESUtils.makePositionCoord(-width * 0.5f, height * 0.5f, width, height);

            GLESVertexInfo vertexInfo = object.getVertexInfo();
            vertexInfo.setBuffer(mDateLabelShader.getPositionAttribIndex(), vertex, 3);

            float[] texCoord = GLESUtils.makeTexCoord(0f, 0f, 1f, 1f);
            vertexInfo.setBuffer(mDateLabelShader.getTexCoordAttribIndex(), texCoord, 2);

            yOffset -= (mDateLabelHeight + mSpacing);

            DateLabelInfo dateLabelInfo = mBucketInfo.get(index);
            yOffset -= (dateLabelInfo.getNumOfRows() * (mColumnWidth + mSpacing));

            ImageObjects imageObjects = mObjectsMap.get(object);

            imageObjects.setStartOffsetY(top - mGridInfo.getDateLabelHeight() - mGridInfo.getSpacing());
            imageObjects.setupObjects(camera);

            index++;
        }
    }

    @Override
    public void onColumnWidthChanged() {
        mPrevColumnWidth = mColumnWidth;
        mColumnWidth = mGridInfo.getColumnWidth();

        mPrevNumOfColumns = mNumOfColumns;
        mNumOfColumns = mGridInfo.getNumOfColumns();

        changeDateLabelObjectPosition();

        int size = mAnimators.size();
        for (int i = 0; i < size; i++) {
            mAnimators.get(i).cancel();
        }

        mAnimators.clear();

        setupAnimations();

        mAnimationFinishCount = 0;
        size = mAnimators.size();
        for (int i = 0; i < size; i++) {
            mAnimators.get(i).start();
        }
    }

    private void changeDateLabelObjectPosition() {
        int index = 0;
        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        float yOffset = mHeight * 0.5f - mActionBarHeight;
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            ImageObjects imageObjects = mObjectsMap.get(object);

            float prevTop = object.getTop();
            object.setPrevLeftTop(object.getLeft(), prevTop);
            imageObjects.setPrevStartOffsetY(prevTop - mDateLabelHeight - mSpacing);

            float nextLeft = mSpacing - mWidth * 0.5f;
            float nextTop = yOffset;

            object.setNextLeftTop(nextLeft, nextTop);
            imageObjects.setNextStartOffsetY(nextTop - mDateLabelHeight - mSpacing);

            yOffset -= (mDateLabelHeight + mSpacing);

            DateLabelInfo dateLabelInfo = mBucketInfo.get(index);
            yOffset -= (dateLabelInfo.getNumOfRows() * (mColumnWidth + mSpacing));

            index++;
        }
    }

    private void setupAnimations() {
        mInvisibleObjects.clear();
        mAnimationObjects.clear();

        float from = 0f;
        float to = 1f;

        GLESAnimator translateAnimator = new GLESAnimator(new TranslateAnimatorCallback());
        translateAnimator.setValues(from, to);
        translateAnimator.setDuration(0L, GalleryConfig.IMAGE_ANIMATION_END_OFFSET);

        mAnimators.add(translateAnimator);

        float fromAlpha = 0f;
        float toAlpha = 1f;

        GLESAnimator alphaAnimator = new GLESAnimator(new AlphaAnimatorCallback());
        alphaAnimator.setValues(fromAlpha, toAlpha);
        alphaAnimator.setDuration(GalleryConfig.DATE_LABEL_ANIMATION_START_OFFSET, GalleryConfig.DATE_LABEL_ANIMATION_END_OFFSET);

        mAnimators.add(alphaAnimator);

        float viewportTop = mHeight * 0.5f - mGridInfo.getTranslateY();
        float viewportBottom = viewportTop - mHeight;

        float nextTranslateY = mRenderer.getNextTranslateY();
        float nextViewportTop = mHeight * 0.5f - nextTranslateY;
        float nextViewportBottom = nextViewportTop - mHeight;

        int index = 0;
        int size = mDateLabelObjects.size();
        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            ImageObjects imageObjects = mObjectsMap.get(object);
            DateLabelInfo dateLabelInfo = mBucketInfo.get(index);
            GalleryNode parentNode = object.getParentNode();

            float prevTop = object.getPrevTop();
            int numOfRows = (int) Math.ceil((double) imageObjects.getNumOfImages() / mPrevNumOfColumns);
            float bottom = prevTop - (mDateLabelHeight + mSpacing) - numOfRows * (mPrevColumnWidth + mSpacing);

            float nextTop = object.getNextTop();
            int nextNumOfRows = dateLabelInfo.getNumOfRows();
            float nextBottom = nextTop - (mDateLabelHeight + mSpacing) - nextNumOfRows * (mColumnWidth + mSpacing);

            if ((nextTop >= nextViewportBottom && nextBottom <= nextViewportTop) ||
                    (prevTop >= viewportBottom && bottom <= viewportTop) ||
                    index == (size - 1)) {
                parentNode.setVisibility(true);

                mAnimationObjects.add(object);
            } else {
                parentNode.setVisibility(false);

                imageObjects.setStartOffsetY(imageObjects.getNextStartOffsetY());

                mInvisibleObjects.put(index, object);
            }

            index++;
        }
    }

    @Override
    public void onNumOfImageInfosChanged() {
        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            ImageObjects imageObjects = mObjectsMap.get(object);

            imageObjects.onNumOfImageInfosChanged();
        }
    }

    @Override
    public void onNumOfDateLabelInfosChanged() {
        mNumOfDateInfos = mGridInfo.getNumOfDateInfos();

        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            ImageObjects imageObjects = mObjectsMap.get(object);

            imageObjects.onNumOfDateLabelInfosChanged();
        }
    }

    // onSurfaceCreated

    public void createObjects(GLESNode parentNode) {
        clear();

        for (int i = 0; i < mNumOfDateInfos; i++) {
            GalleryNode node = new GalleryNode("node" + i);
            parentNode.addChild(node);
            node.setVisibility(false);

            DateLabelObject object = new DateLabelObject("dataIndex" + i);
            mDateLabelObjects.add(object);
            node.addChild(object);
            object.setParentNode(node);

            object.setGLState(mDateLabelGLState);
            object.setShader(mDateLabelShader);
            object.setTexture(mDummyDateLabelTexture);
            object.setListener(mObjectListener);
            object.setIndex(i);

            GLESVertexInfo vertexInfo = new GLESVertexInfo();
            vertexInfo.setRenderType(GLESVertexInfo.RenderType.DRAW_ARRAYS);
            vertexInfo.setPrimitiveMode(GLESVertexInfo.PrimitiveMode.TRIANGLE_STRIP);
            object.setVertexInfo(vertexInfo, false, false);

            DateLabelInfo dateLabelInfo = mBucketInfo.get(i);
            TextureMappingInfo textureMappingInfo = new TextureMappingInfo(object, dateLabelInfo);
            addTextureMapingInfo(textureMappingInfo);

            ImageObjects imageObjects = new ImageObjects(mContext, mRenderer);
            imageObjects.setGLState(mImageGLState);
            imageObjects.setShader(mImageShader);
            imageObjects.setDummyTexture(mDummyImageTexture);
            imageObjects.setDateLabelInfo(dateLabelInfo);
            imageObjects.createObjects(node);
            imageObjects.setSurfaceView(mSurfaceView);
            imageObjects.setGridInfo(mGridInfo);

            mObjectsMap.put(object, imageObjects);
        }
    }

    public void addTextureMapingInfo(TextureMappingInfo info) {
        mTextureMappingInfos.add(info);
    }

    public DateLabelObject getObject(int i) {
        return mDateLabelObjects.get(i);
    }

    // initialization

    public void setSurfaceView(GallerySurfaceView surfaceView) {
        mSurfaceView = surfaceView;
    }

    public void setDateLabelGLState(GLESGLState state) {
        mDateLabelGLState = state;
    }

    public void setImageGLState(GLESGLState state) {
        mImageGLState = state;
    }

    public void setDateLabelShader(GLESShader shader) {
        mDateLabelShader = shader;

        int location = mDateLabelShader.getUniformLocation("uAlpha");
        GLES20.glUniform1f(location, 1f);
    }

    public void setImageShader(GLESShader shader) {
        mImageShader = shader;
    }

    public void setGridInfo(GridInfo gridInfo) {
        mGridInfo = gridInfo;

        mBucketInfo = gridInfo.getBucketInfo();
        mNumOfDateInfos = gridInfo.getNumOfDateInfos();
        mDateLabelHeight = gridInfo.getDateLabelHeight();
        mSpacing = gridInfo.getSpacing();
        mColumnWidth = gridInfo.getColumnWidth();
        mNumOfColumns = gridInfo.getNumOfColumns();
        mActionBarHeight = gridInfo.getActionBarHeight();

        mGridInfo.addListener(this);
    }

    public void setDummyDateLabelTexture(GLESTexture texture) {
        mDummyDateLabelTexture = texture;
    }

    public void setDummyImageTexture(GLESTexture texture) {
        mDummyImageTexture = texture;
    }

    public ImageObject getImageObject(ImageIndexingInfo indexingInfo) {
        DateLabelObject dateLabelObject = mDateLabelObjects.get(indexingInfo.mDateLabelIndex);
        ImageObjects imageObjects = mObjectsMap.get(dateLabelObject);
        ImageObject imageObject = imageObjects.getObject(indexingInfo.mImageIndex);

        return imageObject;
    }

    public void hide() {
        mAlpha = 0f;
    }

    public void makeDateLabel(DateLabelInfo dateLableInfo, GalleryTexture texture) {
        if (BitmapWorker.cancelPotentialWork(dateLableInfo, texture) && texture != null) {
            final DateLabelTask task = new DateLabelTask(mContext, mGridInfo, texture);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(mContext.getResources(),
                            mLoadingBitmap, task);
            DateLabelObject object = (DateLabelObject) mTextureMappingInfos.get(dateLableInfo.getIndex()).getObject();
            object.setTexture(mDummyDateLabelTexture);
            texture.setBitmapDrawable(asyncDrawable);
            task.execute(dateLableInfo);
        }
    }

    public void deleteDateLabel(int index) {
        TextureMappingInfo textureMappingInfo = mTextureMappingInfos.get(index);

        GalleryTexture texture = textureMappingInfo.getTexture();
        texture = null;

        mTextureMappingInfos.remove(index);

        mDateLabelObjects.remove(index);
    }

    public void deleteImage(ImageIndexingInfo indexingInfo) {
        DateLabelObject dateLabelObject = mDateLabelObjects.get(indexingInfo.mDateLabelIndex);
        ImageObjects imageObjects = mObjectsMap.get(dateLabelObject);
        imageObjects.delete(indexingInfo.mImageIndex);
    }

    @Override
    public void onImageLoaded(int position, GalleryTexture texture) {
        mWaitingTextures.add(texture);

        mSurfaceView.requestRender();
    }

    class DateLabelTask extends BitmapWorker.BitmapWorkerTask<GalleryTexture> {
        static final String CLASS = "DateLabelTask";
        static final String TAG = GalleryConfig.TAG + "_" + CLASS;
        static final boolean DEBUG = GalleryConfig.DEBUG;

        private final float DATE_LABEL_TEXT_SIZE = 18f;
        private final float DATE_LABEL_TEXT_SHADOW_RADIUS = 1f;
        private final float DATE_LABEL_TEXT_SHADOW_DX = 0.5f;
        private final float DATE_LABEL_TEXT_SHADOW_DY = 0.5f;
        private final int DATE_LABEL_TEXT_SHADOW_COLOR = 0x88444444;
        private final int DATE_LABEL_TEXT_COLOR = 0xFF222222;

        private final Context mContext;

        private int mSpacing = 0;
        private int mDateLabelHeight = 0;

        private DateLabelInfo mDateLabelInfo = null;

        DateLabelTask(Context context, GridInfo gridInfo, GalleryTexture texture) {
            super(texture);
            mContext = context;

            mSpacing = gridInfo.getSpacing();
            mDateLabelHeight = gridInfo.getDateLabelHeight();
        }

        @Override
        protected BitmapDrawable doInBackground(GalleryInfo... params) {
            mDateLabelInfo = (DateLabelInfo) params[0];

            Paint textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setShadowLayer(
                    GLESUtils.getPixelFromDpi(mContext, DATE_LABEL_TEXT_SHADOW_RADIUS),
                    GLESUtils.getPixelFromDpi(mContext, DATE_LABEL_TEXT_SHADOW_DX),
                    GLESUtils.getPixelFromDpi(mContext, DATE_LABEL_TEXT_SHADOW_DY),
                    DATE_LABEL_TEXT_SHADOW_COLOR);
            textPaint.setTextSize(GLESUtils.getPixelFromDpi(mContext, DATE_LABEL_TEXT_SIZE));
            textPaint.setARGB(0xFF, 0x00, 0x00, 0x00);
            textPaint.setColor(DATE_LABEL_TEXT_COLOR);

            int ascent = (int) Math.ceil(-textPaint.ascent());
            int descent = (int) Math.ceil(textPaint.descent());

            int textHeight = ascent + descent;

            int width = (int) (GLESUtils.getWidthPixels(mContext) - mSpacing * 2);
            int height = mDateLabelHeight;

            int x = mContext.getResources().getDimensionPixelSize(R.dimen.dateindex_padding);
            int y = (height - textHeight) / 2 + ascent;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            options.inSampleSize = 1;
            options.outHeight = height;
            options.outWidth = width;
            Bitmap bitmap = mReusableBitmaps.getBitmapFromReusableSet(options);
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }

            bitmap = GLESUtils.drawTextToBitmap(x, y,
                    mDateLabelInfo.getDate(), textPaint, bitmap);
//            bitmap = GLESUtils.drawTextToBitmap(x, y,
//                    "" + mDateLabelInfo.getIndex(), textPaint, bitmap);

            BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);

            return drawable;
        }
    }

    private void onAnimationFinished() {
        mAnimationFinishCount++;

        int size = mAnimators.size();
        if (mAnimationFinishCount >= size) {
            mRenderer.onAnimationFinished();
            mAnimationFinishCount = 0;
            invalidateObjects();

            mAnimators.clear();
        }
    }

    private void invalidateObjects() {
        int size = mInvisibleObjects.size();
        for (int i = 0; i < size; i++) {
            DateLabelObject object = mInvisibleObjects.valueAt(i);
            invalidateObject(object);
        }

        mInvisibleObjects.clear();

        Iterator<DateLabelObject> iter = mDateLabelObjects.iterator();
        while (iter.hasNext()) {
            DateLabelObject object = iter.next();
            ImageObjects imageObjects = mObjectsMap.get(object);
            imageObjects.invalidateObjects();
        }
    }

    private void invalidateObject(DateLabelObject object) {
        object.setLeftTop(object.getNextLeft(), object.getNextTop());

        float translateX = object.getLeft() + mGridInfo.getDateLabelWidth() * 0.5f;
        float translateY = object.getTop() - mGridInfo.getDateLabelHeight() * 0.5f;

        object.setTranslate(translateX, translateY);
    }

    public void cancelLoading() {
        Iterator<TextureMappingInfo> iter = mTextureMappingInfos.iterator();
        while (iter.hasNext()) {
            TextureMappingInfo info = iter.next();
            GalleryTexture texture = info.getTexture();
            if (texture != null) {
                BitmapWorker.cancelWork(texture);

                mWaitingTextures.remove(texture);

                info.set(null);
            }
        }

        Iterator<DateLabelObject> objectIterator = mDateLabelObjects.iterator();
        while (objectIterator.hasNext()) {
            DateLabelObject object = objectIterator.next();
            ImageObjects imageObjects = mObjectsMap.get(object);
            imageObjects.cancelLoading();
        }
    }

    private GLESObjectListener mObjectListener = new GLESObjectListener() {
        @Override
        public void update(GLESObject object) {
            DateLabelObject dateLabelObject = (DateLabelObject) object;
            GLESTransform transform = object.getTransform();
            transform.setIdentity();
            transform.setTranslate(dateLabelObject.getTranslateX(), dateLabelObject.getTranslateY(), 0f);
        }

        @Override
        public void apply(GLESObject object) {
            int location = mDateLabelShader.getUniformLocation("uAlpha");
            GLES20.glUniform1f(location, mAlpha);
        }
    };


    class AlphaAnimatorCallback implements GLESAnimatorCallback {
        AlphaAnimatorCallback() {
        }

        @Override
        public void onAnimation(GLESVector3 current) {
            mAlpha = current.getX();
        }


        @Override
        public void onCancel() {

        }

        @Override
        public void onFinished() {
            onAnimationFinished();
        }
    }

    class TranslateAnimatorCallback implements GLESAnimatorCallback {
        TranslateAnimatorCallback() {
        }

        @Override
        public void onAnimation(GLESVector3 current) {
            int size = mAnimationObjects.size();
            for (int i = 0; i < size; i++) {
                DateLabelObject object = mAnimationObjects.get(i);

                float prevLeft = object.getPrevLeft();
                float prevTop = object.getPrevTop();

                float nextLeft = object.getNextLeft();
                float nextTop = object.getNextTop();

                float currentLeft = prevLeft + (nextLeft - prevLeft) * current.getX();
                float currentTop = prevTop + (nextTop - prevTop) * current.getX();

                object.setLeftTop(currentLeft, currentTop);

                ImageObjects imageObjects = mObjectsMap.get(object);
                imageObjects.setStartOffsetY(currentTop - mDateLabelHeight - mSpacing);

                float translateX = currentLeft + mGridInfo.getDateLabelWidth() * 0.5f;
                float translateY = currentTop - mDateLabelHeight * 0.5f;

                object.setTranslate(translateX, translateY);
            }
        }

        @Override
        public void onCancel() {

        }

        @Override
        public void onFinished() {
            onAnimationFinished();
        }
    }
}
