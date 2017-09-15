/*******************************************************************************
 * Copyright 2014 Sergey Tarasevich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.itzs.zimageloader.view;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView.ScaleType;

import com.itzs.zimageloader.FileNameGenerator;
import com.itzs.zimageloader.ImageLoader;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * Wrapper for Android {@link android.view.View View}. Keeps weak reference of View to prevent memory leaks.
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.9.2
 */
public abstract class ViewAware {

    private final String TAG = ViewAware.class.getSimpleName();

    public static final String WARN_CANT_SET_DRAWABLE = "Can't set a drawable into view. You should call ImageLoader on UI thread for it.";
    public static final String WARN_CANT_SET_BITMAP = "Can't set a bitmap into view. You should call ImageLoader on UI thread for it.";

    protected Reference<View> viewRef;
    protected String uri;
    protected String memoryCacheKey;
    protected boolean checkActualViewSize;
    /**
     * 标记是否允许压缩图片
     */
    protected final boolean shouldCompress;
    /**
     * 记录要压缩图片的目标大小，0 width， 1 height
     */
    protected int[] targetSize = new int[2];


    public ViewAware(View view, String uri) {
        this(view, uri, true);
    }

    public ViewAware(View view, String uri, boolean shoudCompress) {
        this(view, uri, true, shoudCompress);
    }

    public ViewAware(View view, String uri, boolean checkActualViewSize, boolean shouldCompress) {
        if (view == null) throw new IllegalArgumentException("view 不可为 null");
        this.viewRef = new WeakReference<View>(view);
        this.uri = uri;
        this.checkActualViewSize = checkActualViewSize;
        this.shouldCompress = shouldCompress;
    }

    public int getId() {
        View view = viewRef.get();
        return view == null ? super.hashCode() : view.hashCode();
    }

    public ScaleType getScaleType() {
        return ScaleType.CENTER_CROP;
    }

    public View getWrappedView() {
        return viewRef.get();
    }

    public boolean isCollected() {
        return viewRef.get() == null;
    }


    public String getUri() {
        return uri;
    }

    public String getMemoryCacheKey() {
        if (null == memoryCacheKey) {
            //默认情况下memoryCacheKey = uri；
            memoryCacheKey = uri;
            if (shouldCompress) {
                //是否压缩图片，如果压缩图片则memoryCacheKey的值为uri_width X heigth;
                initTagetSize();
                memoryCacheKey = FileNameGenerator.generateMemoryCacheKey(uri, targetSize);
            }
        }
        return memoryCacheKey;
    }

    public boolean setImageDrawable(Drawable drawable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            View view = viewRef.get();
            if (view != null) {
                setImageDrawableInto(drawable, view);
                return true;
            }
        } else {
            Log.w(TAG, WARN_CANT_SET_DRAWABLE);
        }
        return false;
    }

    public boolean setImageBitmap(Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            View view = viewRef.get();
            if (view != null) {
                setImageBitmapInto(bitmap, view);
                return true;
            }
        } else {
            Log.w(TAG, WARN_CANT_SET_BITMAP);
        }
        return false;
    }

    /**
     * 标记是否允许压缩图片
     *
     * @return
     */
    public boolean isShouldCompress() {
        return shouldCompress;
    }

    /**
     * 设置压缩图片目标大小targetSize的值
     *
     * @param targetSize
     */
    public void setTargetSize(int[] targetSize) {
        this.targetSize = targetSize;
    }

    /**
     * 设置压缩图片目标大小targetSize的值
     *
     * @return
     */
    public int[] getTargetSize() {
        return this.targetSize;
    }

    /**
     * 为指定view定义压缩图片的目标尺寸，<br/>
     * 如果用户没有设置明确的大小，则自动获取view的实际大小作为目标尺寸，<br/>
     * 如果view的实际大小获取失败则使用默认大小作为目标尺寸.
     */
    private void initTagetSize() {
        if (targetSize[0] > 0 && targetSize[1] > 0) {
            //已有合法的targetSize
            return;
        } else {
            //targetSize不合法
            targetSize[0] = getWidth();
            if (targetSize[0] <= 0) targetSize[0] = ImageLoader.getDefaultWidht();

            targetSize[1] = getHeight();
            if (targetSize[1] <= 0) targetSize[1] = ImageLoader.getDefaultWidht();
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Width is defined by target {@link android.view.View view} parameters, configuration
     * parameters or device display dimensions.<br />
     * Size computing algorithm (go by steps until get non-zero value):<br />
     * 1) Get the actual drawn <b>getWidth()</b> of the View<br />
     * 2) Get <b>layout_width</b>
     */
    protected int getWidth() {
        View view = viewRef.get();
        if (view != null) {
            final ViewGroup.LayoutParams params = view.getLayoutParams();
            int width = 0;
            if (checkActualViewSize && params != null && params.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                width = view.getWidth(); // Get actual image width
            }
            if (width <= 0 && params != null) width = params.width; // Get layout width parameter
            return width;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Height is defined by target {@link android.view.View view} parameters, configuration
     * parameters or device display dimensions.<br />
     * Size computing algorithm (go by steps until get non-zero value):<br />
     * 1) Get the actual drawn <b>getHeight()</b> of the View<br />
     * 2) Get <b>layout_height</b>
     */
    protected int getHeight() {
        View view = viewRef.get();
        if (view != null) {
            final ViewGroup.LayoutParams params = view.getLayoutParams();
            int height = 0;
            if (checkActualViewSize && params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                height = view.getHeight(); // Get actual image height
            }
            if (height <= 0 && params != null)
                height = params.height; // Get layout height parameter
            return height;
        }
        return 0;
    }

    /**
     * Should set drawable into incoming view. Incoming view is guaranteed not null.<br />
     * This method is called on UI thread.
     */
    protected abstract void setImageDrawableInto(Drawable drawable, View view);

    /**
     * Should set Bitmap into incoming view. Incoming view is guaranteed not null.< br />
     * This method is called on UI thread.
     */
    protected abstract void setImageBitmapInto(Bitmap bitmap, View view);
}