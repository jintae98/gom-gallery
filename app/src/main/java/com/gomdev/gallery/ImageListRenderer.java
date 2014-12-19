package com.gomdev.gallery;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import com.gomdev.gles.GLESCamera;
import com.gomdev.gles.GLESContext;
import com.gomdev.gles.GLESGLState;
import com.gomdev.gles.GLESNode;
import com.gomdev.gles.GLESRect;
import com.gomdev.gles.GLESRenderer;
import com.gomdev.gles.GLESSceneManager;
import com.gomdev.gles.GLESShader;
import com.gomdev.gles.GLESShaderConstant;
import com.gomdev.gles.GLESTexture;
import com.gomdev.gles.GLESTexture2D;
import com.gomdev.gles.GLESTransform;
import com.gomdev.gles.GLESUtils;
import com.gomdev.gles.GLESVertexInfo;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by gomdev on 14. 12. 16..
 */
public class ImageListRenderer implements GLSurfaceView.Renderer {
    static final String CLASS = "ImageListRenderer";
    static final String TAG = GalleryConfig.TAG + "_" + CLASS;
    static final boolean DEBUG = GalleryConfig.DEBUG;

    private static GLESTexture sDummyTexture = null;

    private final Context mContext;
    private final ImageManager mImageManager;


    private GLESRenderer mRenderer;
    private GLESSceneManager mSM;
    private GLESNode mRoot;
    private GLESGLState mGLState;
    private GalleryObject[] mObjects;
    private GLESShader mShader;
    private GLSurfaceView mSurfaceView = null;

    private float mScreenRatio = 1f;
    private int mColumnWidth = 0;
    private int mNumOfColumns = 3;
    private int mNumOfRows = 0;
    private int mNumOfObjects = 0;
    private int mActionBarHeight = 0;
    private int mSpace = 0;
    private int mWidth = 0;
    private int mHeight = 0;

    private int mNumOfImages = 0;

    private BucketInfo mBucketInfo;

    public ImageListRenderer(Context context) {
        mContext = context;
        GLESContext.getInstance().setContext(context);

        GalleryContext galleryContext = GalleryContext.getInstance();
        mColumnWidth = galleryContext.getColumnWidth();
        mNumOfColumns = galleryContext.getNumOfColumns();
        mActionBarHeight = galleryContext.getActionBarHeight();

        mSpace = context.getResources().getDimensionPixelSize(R.dimen.gridview_spacing);

        mNumOfRows = calcNumOfRows(context);
        mNumOfObjects = mNumOfColumns * mNumOfRows;

        mImageManager = ImageManager.getInstance();

        setupSceneComponent();
    }

    private int calcNumOfRows(Context context) {
        DisplayMetrics matrics = context.getResources().getDisplayMetrics();
        int height = matrics.heightPixels;
        return (height / (mColumnWidth + mSpace)) + 1;
    }

    private void setupSceneComponent() {
        mRenderer = GLESRenderer.createRenderer();

        mSM = GLESSceneManager.createSceneManager();
        mRoot = mSM.createRootNode("root");

        mGLState = new GLESGLState();
        mGLState.setCullFaceState(true);
        mGLState.setCullFace(GLES20.GL_BACK);
        mGLState.setDepthState(true);
        mGLState.setDepthFunc(GLES20.GL_LEQUAL);
    }


    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        update();

        mRenderer.updateScene(mSM);
        mRenderer.drawScene(mSM);
    }

    private void update() {
        float xOffset = -(mWidth * 0.5f) + mColumnWidth * 0.5f + mSpace;
        float yOffset = (mHeight * 0.5f) - mColumnWidth * 0.5f - mSpace - mActionBarHeight;
        for (int i = 0; i < mNumOfObjects; i++) {
            GLESTransform transform = mObjects[i].getTransform();
            transform.setIdentity();

            int rowIndex = i / mNumOfColumns;
            int columnIndex = i % mNumOfColumns;
            float x = xOffset + (mColumnWidth + mSpace) * columnIndex;
            float y = yOffset - (mColumnWidth + mSpace) * rowIndex;

            transform.translate(x, y, 0f);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mRenderer.reset();

        mWidth = width;
        mHeight = height;

        mScreenRatio = (float) width / height;

        GLESCamera camera = setupCamera(width, height);

        for (int i = 0; i < mNumOfObjects; i++) {
            mObjects[i].setCamera(camera);
        }

        ImageInfo imageInfo = mBucketInfo.get(0);

        for (int i = 0; i < mNumOfObjects; i++) {
            GLESVertexInfo vertexInfo = createPlaneVertexInfo(mColumnWidth, mColumnWidth, imageInfo);
            mObjects[i].setVertexInfo(vertexInfo, false, false);

            GalleryTexture texture = new GalleryTexture(imageInfo.getWidth(), imageInfo.getHeight());
            texture.setObject(mObjects[i]);
            texture.setSurfaceView(mSurfaceView);
            texture.setBucketInfo(mBucketInfo);
            texture.setPosition(i);
            mObjects[i].setTextureReference(texture);

            mObjects[i].setTexture(sDummyTexture);
        }
    }

    private GLESVertexInfo createPlaneVertexInfo(float width, float height, ImageInfo imageInfo) {
        float right = width * 0.5f;
        float left = -right;
        float top = height * 0.5f;
        float bottom = -top;
        float z = 0.0f;

        float[] vertex = {
                left, bottom, z,
                right, bottom, z,
                left, top, z,
                right, top, z
        };

        GLESVertexInfo vertexInfo = new GLESVertexInfo();

        vertexInfo.setBuffer(mShader.getPositionAttribIndex(), vertex, 3);

        float[] texCoord = {
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };

        vertexInfo.setBuffer(mShader.getTexCoordAttribIndex(), texCoord, 2);

        vertexInfo.setRenderType(GLESVertexInfo.RenderType.DRAW_ARRAYS);
        vertexInfo.setPrimitiveMode(GLESVertexInfo.PrimitiveMode.TRIANGLE_STRIP);

        return vertexInfo;
    }

    private GLESCamera setupCamera(int width, int height) {
        GLESCamera camera = new GLESCamera();

        float fovy = 30f;
        float eyeZ = (height / 2f) / (float) Math.tan(Math.toRadians(fovy * 0.5));

        camera.setLookAt(0f, 0f, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f);

        camera.setFrustum(fovy, mScreenRatio, eyeZ * 0.1f, eyeZ * 2f);

        camera.setViewport(new GLESRect(0, 0, width, height));

        return camera;
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(1f, 1f, 1f, 1f);

        createShader();

        if (null == sDummyTexture) {
            Bitmap bitmap = GLESUtils.makeBitmap(512, 512, Bitmap.Config.ARGB_8888, Color.BLACK);
            sDummyTexture = new GLESTexture2D(512, 512, bitmap);
            sDummyTexture.load();
        }
    }

    private boolean createShader() {
        if (DEBUG) {
            Log.d(TAG, "createShader()");
        }

        mShader = new GLESShader(mContext);

        String vsSource = GLESUtils.getStringFromReosurce(mContext, R.raw.texture_20_vs);
        String fsSource = GLESUtils.getStringFromReosurce(mContext, R.raw.texture_20_fs);

        mShader.setShaderSource(vsSource, fsSource);
        if (mShader.load() == false) {
            return false;
        }

        String attribName = GLESShaderConstant.ATTRIB_POSITION;
        mShader.setPositionAttribIndex(attribName);

        attribName = GLESShaderConstant.ATTRIB_TEXCOORD;
        mShader.setTexCoordAttribIndex(attribName);

        for (int i = 0; i < mNumOfObjects; i++) {
            mObjects[i].setShader(mShader);
        }

        return true;
    }

    public void setBucketInfo(BucketInfo bucketInfo) {
        mBucketInfo = bucketInfo;
        mNumOfImages = mBucketInfo.getNumOfImageInfos();

        if (mNumOfImages < mNumOfObjects) {
            mNumOfObjects = mNumOfImages;
        }

        createObjects();
    }

    private void createObjects() {
        mObjects = new GalleryObject[mNumOfObjects];
        for (int i = 0; i < mNumOfObjects; i++) {
            mObjects[i] = new GalleryObject("image" + i);
            mRoot.addChild(mObjects[i]);
            mObjects[i].setGLState(mGLState);
        }
    }

    public void setSurfaceView(GLSurfaceView surfaceView) {
        mSurfaceView = surfaceView;
    }
}
