package com.itzs.zimageloader;

import com.itzs.zimageloader.view.ImageViewAware;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ImageLoaderEngine {

    /**
     * 用于从非本地缓存（如网络）加载的线程池
     */
    private Executor executorDownload;
    /**
     * 用于从本地缓存加载的线程池
     */
    private Executor executorCached;
    /**
     * 用于任务分发的线程池
     */
    private Executor executorDistributor;

    private LruDiskCache diskCache;

    private final Map<Integer, String> cacheKeysForImageAwares = Collections
            .synchronizedMap(new HashMap<Integer, String>());
    /**
     * url锁，为每个url分配一个锁，当下载url时，锁住，直到下载url结束才释放锁，以此实现了禁止同时下载同一个url的问题；
     */
    private final Map<String, ReentrantLock> uriLocks = new WeakHashMap<String, ReentrantLock>();

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean networkDenied = new AtomicBoolean(false);
    private final AtomicBoolean slowNetwork = new AtomicBoolean(false);

    private final Object pauseLock = new Object();

    ImageLoaderEngine(LruDiskCache diskCache) {
        this.diskCache = diskCache;
        initExecutorsIfNeed();
    }

    /**
     * 创建线程池
     */
    private void initExecutorsIfNeed() {
        if (null == executorDownload || ((ExecutorService) executorDownload).isShutdown()) {
            executorDownload = DefaultConfigurationFactory.createExecutor();
        }
        if (null == executorCached || ((ExecutorService) executorCached).isShutdown()) {
            executorCached = DefaultConfigurationFactory.createExecutor();
        }
        if (null == executorDistributor || ((ExecutorService) executorDistributor).isShutdown()) {
            executorDistributor = DefaultConfigurationFactory.createTaskDistributor();
        }
    }

    /**
     * 将任务提交到线程池排队执行
     */
    void submit(final LoadAndDisplayImageTask task) {
        executorDistributor.execute(new Runnable() {
            @Override
            public void run() {
                File image = diskCache.get(task.getLoadingUri());
                boolean isImageCachedOnDisk = (image != null && image.exists());
                initExecutorsIfNeed();
                if (isImageCachedOnDisk) {
                    executorCached.execute(task);
                } else {
                    executorDownload.execute(task);
                }
            }
        });
    }

    /**
     * 获取当前ImageViewAware正在现在的uri
     */
    String getLoadingUriForView(ImageViewAware imageAware) {
        return cacheKeysForImageAwares.get(imageAware.getId());
    }

    /**
     * 将memoryCacheKey和ImageViewAware绑定
     */
    void prepareDisplayTaskFor(ImageViewAware imageAware) {
        cacheKeysForImageAwares.put(imageAware.getId(), imageAware.getMemoryCacheKey());
    }

    /**
     * 取消ImageViewAware的下载任务
     */
    void cancelDisplayTaskFor(ImageViewAware imageAware) {
        cacheKeysForImageAwares.remove(imageAware.getId());
    }

    /**
     * 禁止从网络加载图（如果本地存在缓存，会加载缓存）
     */
    void denyNetworkDownloads(boolean denyNetworkDownloads) {
        networkDenied.set(denyNetworkDownloads);
    }

    /**
     * 是否使用在从网络加载时使用FlushedInputStream来解决 <a
     * href="http://code.google.com/p/android/issues/detail?id=6066">这个已知问题</a>
     *
     * @param handleSlowNetwork <b>true</b> - 使用 FlushedInputStream 进行网络加载; <b>false</b>- 不使用。
     */
    void handleSlowNetwork(boolean handleSlowNetwork) {
        slowNetwork.set(handleSlowNetwork);
    }

    /**
     * 获得指定uri的锁
     * @param uri
     * @return
     */
    ReentrantLock getLockForUri(String uri) {
        ReentrantLock lock = uriLocks.get(uri);
        if (lock == null) {
            lock = new ReentrantLock();
            uriLocks.put(uri, lock);
        }
        return lock;
    }

    /**
     * 加载引擎是否暂停
     * @return
     */
    AtomicBoolean getPause() {
        return paused;
    }

    /**
     * 获得加载引擎的暂停锁
     * @return
     */
    Object getPauseLock() {
        return pauseLock;
    }

    /**
     * 是否禁止网络加载
     * @return
     */
    boolean isNetworkDenied() {
        return networkDenied.get();
    }

    /**
     * 是否使用在从网络加载时使用FlushedInputStream来解决 <a
     * href="http://code.google.com/p/android/issues/detail?id=6066">这个已知问题</a>
     */
    boolean isSlowNetwork() {
        return slowNetwork.get();
    }

    /**
     * 暂停加载引擎，所有未执行的任务都会暂停，知道引擎恢复运行；已开始执行的任务会继续执行；
     */
    void pause() {
        paused.set(true);
    }

    /**
     * 加载引擎恢复运行
     */
    void resume() {
        paused.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    /**
     * 停止加载引擎，所有任务都将停止，包括未运行的和正在运行的；并会清空本次运行的数据
     */
    void stop() {
        ((ExecutorService) executorDownload).shutdownNow();
        ((ExecutorService) executorCached).shutdownNow();

        cacheKeysForImageAwares.clear();
        uriLocks.clear();
    }

    void fireCallback(Runnable r) {
        executorDistributor.execute(r);
    }

}
