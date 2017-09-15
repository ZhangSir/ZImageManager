package com.itzs.zimageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.itzs.zimageloader.decoder.BaseDecoder;
import com.itzs.zimageloader.downloader.BaseDownloader;
import com.itzs.zimageloader.view.ImageViewAware;

public class ImageLoader {
    public static final String TAG = ImageLoader.class.getSimpleName();

    public static int imageResourseOnLoading = R.mipmap.ic_stub;
    public static int imageResourseOnFail = R.mipmap.ic_error;
    public static int imageResourseOnEmptyUri = R.mipmap.ic_empty;
    /**
     * 记录屏幕宽度
     */
    private static int DISPLAY_WIDTH;
    /**
     * 记录屏幕高度
     */
    private static int DISPLAY_HEIGHT;

    private ImageLoaderEngine engine;

    private BaseDownloader downloader, networkDeniedDownloader, slowNetworkDownloader;
    private LruMemoryCache memoryCache;
    private LruDiskCache diskCache;
    private BaseDecoder decoder;

    private Context context;

    private Handler handler = null;

    private volatile static ImageLoader instance;

    public static int getDefaultWidht() {
        return DISPLAY_WIDTH > 0 ? DISPLAY_WIDTH / 2 : 540;
    }

    public static int getDefaultHeight() {
        return DISPLAY_HEIGHT > 0 ? DISPLAY_HEIGHT / 2 : 960;
    }


    /**
     * 返回单例实例
     */
    public static ImageLoader getInstance(Context context) {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader(context);
                }
            }
        }
        return instance;
    }

    private ImageLoader(Context context) {
        this.context = context;
        DISPLAY_WIDTH = this.context.getResources().getDisplayMetrics().widthPixels;
        DISPLAY_HEIGHT = this.context.getResources().getDisplayMetrics().heightPixels;
        downloader = DefaultConfigurationFactory.createImageDownloader(context);
        networkDeniedDownloader = DefaultConfigurationFactory.createNetworkDeniedDownloader(downloader);
        slowNetworkDownloader = DefaultConfigurationFactory.createSlowNetworkDownloader(downloader);
        diskCache = DefaultConfigurationFactory.createDiskCache();
        memoryCache = DefaultConfigurationFactory.createMemoryCache(context);
        decoder = DefaultConfigurationFactory.createImageDecoder();
        engine = new ImageLoaderEngine(diskCache);
    }

    public Drawable getImageOnLoading() {
        return context.getResources().getDrawable(imageResourseOnLoading);
    }

    public Drawable getImageOnFail() {
        return context.getResources().getDrawable(imageResourseOnFail);
    }

    public Drawable getImageOnEmptyUri() {
        return context.getResources().getDrawable(imageResourseOnEmptyUri);
    }

    public void displayImage(ImageViewAware imageAware,
                             ImageLoadingListener listener) {
        if (imageAware == null) {
            throw new IllegalArgumentException("displayImage方法调用参数错误，ImageAware不可为null");
        }

        if (TextUtils.isEmpty(imageAware.getUri())) {
            engine.cancelDisplayTaskFor(imageAware);
            listener.onLoadingStarted(imageAware.getUri(), imageAware.getWrappedView());
            imageAware.setImageDrawable(getImageOnEmptyUri());
            listener.onLoadingComplete(imageAware.getUri(), imageAware.getWrappedView(), null);
            return;
        }

        engine.prepareDisplayTaskFor(imageAware);

        listener.onLoadingStarted(imageAware.getUri(), imageAware.getWrappedView());

        Bitmap bmp = memoryCache.get(imageAware.getMemoryCacheKey());
        if (bmp != null && !bmp.isRecycled()) {
            Log.d(TAG, "从缓存中获取到图片-->" + imageAware.getMemoryCacheKey());
            imageAware.setImageBitmap(bmp);
            listener.onLoadingComplete(imageAware.getUri(), imageAware.getWrappedView(), bmp);
        } else {
            imageAware.setImageDrawable(getImageOnLoading());

            LoadAndDisplayImageTask displayTask = new LoadAndDisplayImageTask(
                    imageAware,
                    instance,
                    listener);
            engine.submit(displayTask);
        }
    }

    public Handler getHandler() {
        if (handler == null && Looper.myLooper() == Looper.getMainLooper()) {
            handler = new Handler();
        }
        return handler;
    }

    public ImageLoaderEngine getEngine() {
        return engine;
    }

    public BaseDownloader getDownloader() {
        return downloader;
    }

    public BaseDownloader getNetworkDeniedDownloader() {
        return networkDeniedDownloader;
    }

    public BaseDownloader getSlowNetworkDownloader() {
        return slowNetworkDownloader;
    }

    public BaseDecoder getDecoder() {
        return decoder;
    }

    public LruMemoryCache getMemoryCache() {
        return memoryCache;
    }

    public void clearMemoryCache() {
        memoryCache.clear();
    }

    public LruDiskCache getDiskCache() {
        return diskCache;
    }

    public void clearDiskCache() {
        diskCache.clear();
    }

    public String getLoadingUriForView(ImageViewAware imageAware) {
        return engine.getLoadingUriForView(imageAware);
    }

    public String getLoadingUriForView(ImageView imageView) {
        return engine.getLoadingUriForView(new ImageViewAware(imageView, null));
    }

    public void cancelDisplayTask(ImageViewAware imageAware) {
        engine.cancelDisplayTaskFor(imageAware);
    }

    public void cancelDisplayTask(ImageView imageView) {
        engine.cancelDisplayTaskFor(new ImageViewAware(imageView, null));
    }

    public void denyNetworkDownloads(boolean denyNetworkDownloads) {
        engine.denyNetworkDownloads(denyNetworkDownloads);
    }

    /**
     * 是否使用 FlushedInputStream 来解决这个已知的问题<br/>
     * http://code.google.com/p/android/issues/detail?id=6066
     *
     * @param handleSlowNetwork pass <b>true</b> - to use FlushedInputStream for network downloads; <b>false</b>
     *                          - otherwise.
     */
    public void handleSlowNetwork(boolean handleSlowNetwork) {
        engine.handleSlowNetwork(handleSlowNetwork);
    }

    /**
     * Pause ImageLoader. All new "load&display" tasks won't be executed until ImageLoader is {@link #resume() resumed}.
     * <br />
     * Already running tasks are not paused.
     */
    public void pause() {
        engine.pause();
    }

    /**
     * Resumes waiting "load&display" tasks
     */
    public void resume() {
        engine.resume();
    }

    /**
     * Cancels all running and scheduled display image tasks.<br />
     * <b>NOTE:</b> This method doesn't shutdown custom task executors if you set them.<br />
     * ImageLoader still can be used after calling this method.
     */
    public void stop() {
        engine.stop();
    }

    public void destroy() {
        stop();
        downloader = null;
        decoder = null;
        memoryCache = null;
        diskCache = null;
        engine = null;
        instance = null;
    }
}

