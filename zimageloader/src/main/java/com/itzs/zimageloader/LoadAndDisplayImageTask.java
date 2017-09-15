package com.itzs.zimageloader;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.itzs.zimageloader.decoder.BaseDecoder;
import com.itzs.zimageloader.downloader.BaseDownloader;
import com.itzs.zimageloader.view.ImageViewAware;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class LoadAndDisplayImageTask implements Runnable, IoUtils.CopyListener {

    private static final String TAG = LoadAndDisplayImageTask.class.getSimpleName();

    private ImageLoader loader;
    private final ImageLoaderEngine engine;
    private final Handler handler;

    private final BaseDecoder decoder;
    private final String uri;
    private final String memoryCacheKey;
    private final ImageViewAware imageAware;
    private final ImageLoadingListener listener;
    private ReentrantLock loadingUriLock;
    private LruMemoryCache memoryCache;
    private LruDiskCache diskCache;

    public LoadAndDisplayImageTask(ImageViewAware imageAware,
                                   ImageLoader loader,
                                   ImageLoadingListener listener) {
        this.loader = loader;
        this.imageAware = imageAware;
        this.listener = listener;

        this.engine = this.loader.getEngine();
        this.decoder = this.loader.getDecoder();
        this.memoryCache = this.loader.getMemoryCache();
        this.diskCache = this.loader.getDiskCache();
        this.handler = this.loader.getHandler();

        this.uri = this.imageAware.getUri();
        this.memoryCacheKey = this.imageAware.getMemoryCacheKey();

        this.loadingUriLock = this.engine.getLockForUri(imageAware.getUri());
    }

    @Override
    public void run() {
        if (waitIfPaused()) return;

        Log.d(TAG, "开始执行加载任务-->" + memoryCacheKey);
        if (loadingUriLock.isLocked()) {
            Log.d(TAG, "图片已经在加载：！" + uri);
        }
        loadingUriLock.lock();
        Bitmap bmp;
        try {
            checkTaskNotActual();
            bmp = memoryCache.get(memoryCacheKey);
            if (bmp == null || bmp.isRecycled()) {
                bmp = tryLoadBitmap();
                if (bmp == null) return; // listener callback already was fired

                checkTaskNotActual();
                checkTaskInterrupted();

                Log.d(TAG, "将图片加载进缓存-->" + memoryCacheKey);
                memoryCache.put(memoryCacheKey, bmp);
            } else {
                Log.d(TAG, "图片已存在在缓存中-->" + memoryCacheKey);
            }

            checkTaskNotActual();
            checkTaskInterrupted();
        } catch (TaskCancelledException e) {
            fireCancelEvent();
            return;
        } finally {
            loadingUriLock.unlock();
        }

        DisplayBitmapTask displayBitmapTask = new DisplayBitmapTask(bmp, uri, memoryCacheKey, imageAware, listener, engine);
        runTask(displayBitmapTask, handler, engine);
    }

    /**
     * @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise
     */
    private boolean waitIfPaused() {
        AtomicBoolean pause = engine.getPause();
        if (pause.get()) {
            synchronized (engine.getPauseLock()) {
                if (pause.get()) {
                    Log.d(TAG, "ImageLoader已被暂停，等待。。。" + memoryCacheKey);
                    try {
                        engine.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "ImageLoader暂停等待被打断，任务终止：" + memoryCacheKey);
                        return true;
                    }
                    Log.d(TAG, "ImageLoader从暂停中恢复：" + memoryCacheKey);
                }
            }
        }
        return isTaskNotActual();
    }

    private Bitmap tryLoadBitmap() throws TaskCancelledException {
        Bitmap bitmap = null;
        try {
            File imageFile = diskCache.get(uri);
            if (imageFile != null && imageFile.exists()) {
                Log.d(TAG, "从本地磁盘加载图片-->" + memoryCacheKey);
                checkTaskNotActual();
                bitmap = decodeImage(BaseDownloader.Scheme.FILE.wrap(imageFile.getAbsolutePath()));
            }
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                Log.d(TAG, "从网络加载图片-->" + memoryCacheKey);

                String imageUriForDecoding = uri;
                if (tryCacheImageOnDisk()) {
                    imageFile = diskCache.get(uri);
                    if (imageFile != null) {
                        imageUriForDecoding = BaseDownloader.Scheme.FILE.wrap(imageFile.getAbsolutePath());
                    }
                }

                checkTaskNotActual();
                bitmap = decodeImage(imageUriForDecoding);

                if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    fireFailEvent(FailReason.FailType.DECODING_ERROR, null);
                }
            }
        } catch (IllegalStateException e) {
            fireFailEvent(FailReason.FailType.NETWORK_DENIED, null);
        } catch (TaskCancelledException e) {
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "tryLoadBitmap", e);
            fireFailEvent(FailReason.FailType.IO_ERROR, e);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "tryLoadBitmap", e);
            fireFailEvent(FailReason.FailType.OUT_OF_MEMORY, e);
        } catch (Throwable e) {
            Log.e(TAG, "tryLoadBitmap", e);
            fireFailEvent(FailReason.FailType.UNKNOWN, e);
        }
        return bitmap;
    }

    private Bitmap decodeImage(String imageUri) throws IOException {
        Log.d(TAG, "memoryCacheKey-->" + memoryCacheKey);
        Log.d(TAG, "imageUri-->" + imageUri);
        return decoder.decode(imageUri, imageAware, getDownloader(), null);
    }

    /**
     * @return <b>true</b> - if image was downloaded successfully; <b>false</b> - otherwise
     */
    private boolean tryCacheImageOnDisk() throws TaskCancelledException {
        Log.d(TAG, "将图片缓存到磁盘-->" + memoryCacheKey);

        boolean loaded;
        try {
            loaded = downloadImage();
        } catch (IOException e) {
            Log.e(TAG, "tryCacheImageOnDisk", e);
            loaded = false;
        }
        return loaded;
    }

    private boolean downloadImage() throws IOException {
        InputStream is = getDownloader().getStream(uri, null);
        return diskCache.put(uri, is, this);
    }

    private BaseDownloader getDownloader() {
        if (engine.isNetworkDenied()) {
            return loader.getNetworkDeniedDownloader();
        } else if (engine.isSlowNetwork()) {
            return loader.getSlowNetworkDownloader();
        } else {
            return loader.getDownloader();
        }
    }

    @Override
    public boolean onBytesCopied(int current, int total) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onBytesCopied, curren: " + current + " total: " + total);
        return fireProgressEvent(current, total);
    }

    /**
     * @return <b>true</b> - if loading should be continued; <b>false</b> - if loading should be interrupted
     */
    private boolean fireProgressEvent(final int current, final int total) {
        if (isTaskInterrupted() || isTaskNotActual()) return false;
        if (listener != null) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    listener.onProgressUpdate(uri, imageAware.getWrappedView(), current, total);
                }
            };
            runTask(r, handler, engine);
        }
        return true;
    }

    private void fireFailEvent(final FailReason.FailType failType, final Throwable failCause) {
        if (isTaskInterrupted() || isTaskNotActual()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                imageAware.setImageDrawable(loader.getImageOnFail());
                listener.onLoadingFailed(uri, imageAware.getWrappedView(), new FailReason(failType, failCause));
            }
        };
        runTask(r, handler, engine);
    }

    private void fireCancelEvent() {
        if (isTaskInterrupted()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                listener.onLoadingCancelled(uri, imageAware.getWrappedView());
            }
        };
        runTask(r, handler, engine);
    }

    /**
     * @throws TaskCancelledException if task is not actual (target ImageAware is collected by GC or the image URI of
     *                                this task doesn't match to image URI which is actual for current ImageAware at
     *                                this moment)
     */
    private void checkTaskNotActual() throws TaskCancelledException {
        checkViewCollected();
        checkViewReused();
    }

    /**
     * @throws TaskCancelledException if target ImageAware is collected
     */
    private void checkViewCollected() throws TaskCancelledException {
        if (isViewCollected()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * @throws TaskCancelledException if target ImageAware is collected by GC
     */
    private void checkViewReused() throws TaskCancelledException {
        if (isViewReused()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * @return <b>true</b> - if task is not actual (target ImageAware is collected by GC or the image URI of this task
     * doesn't match to image URI which is actual for current ImageAware at this moment)); <b>false</b> - otherwise
     */
    private boolean isTaskNotActual() {
        return isViewCollected() || isViewReused();
    }

    /**
     * @return <b>true</b> - if target ImageAware is collected by GC; <b>false</b> - otherwise
     */
    private boolean isViewCollected() {
        if (imageAware.isCollected()) {
            Log.d(TAG, "ViewAware已经被GC回收-->" + memoryCacheKey);
            return true;
        }
        return false;
    }

    /**
     * @return <b>true</b> - if current ImageAware is reused for displaying another image; <b>false</b> - otherwise
     */
    private boolean isViewReused() {
        String currentCacheKey = engine.getLoadingUriForView(imageAware);
        // Check whether memory cache key (image URI) for current ImageAware is actual.
        // If ImageAware is reused for another task then current task should be cancelled.
        boolean imageAwareWasReused = !memoryCacheKey.equals(currentCacheKey);
        if (imageAwareWasReused) {
            Log.d(TAG, "ViewAware被重新用来加载其他图片-->" + memoryCacheKey);
            return true;
        }
        return false;
    }

    /**
     * @throws TaskCancelledException if current task was interrupted
     */
    private void checkTaskInterrupted() throws TaskCancelledException {
        if (isTaskInterrupted()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * @return <b>true</b> - if current task was interrupted; <b>false</b> - otherwise
     */
    private boolean isTaskInterrupted() {
        if (Thread.interrupted()) {
            Log.d(TAG, "任务被打断-->" + memoryCacheKey);
            return true;
        }
        return false;
    }


    String getLoadingUri() {
        return uri;
    }

    static void runTask(Runnable r, Handler handler, ImageLoaderEngine engine) {
        if (handler == null) {
            engine.fireCallback(r);
        } else {
            handler.post(r);
        }
    }

    /**
     * Exceptions for case when task is cancelled (thread is interrupted, image view is reused for another task, view is
     * collected by GC).
     *
     * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
     * @since 1.9.1
     */
    class TaskCancelledException extends Exception {
    }
}
