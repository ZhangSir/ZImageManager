/*******************************************************************************
 * Copyright 2011-2013 Sergey Tarasevich
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
package com.itzs.zimageloader;

import android.content.Context;
import android.os.Environment;

import com.itzs.zimageloader.decoder.ImageDecoder;
import com.itzs.zimageloader.decoder.BaseDecoder;
import com.itzs.zimageloader.downloader.ImageDownloader;
import com.itzs.zimageloader.downloader.BaseDownloader;
import com.itzs.zimageloader.downloader.NetworkDeniedImageDownloader;
import com.itzs.zimageloader.downloader.SlowNetworkImageDownloader;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认的配置工厂
 */
public class DefaultConfigurationFactory {

    /** 默认的线程池大小 */
    public static final int DEFAULT_THREAD_POOL_SIZE = 3;
    /**默认的线程优先级 */
    public static final int DEFAULT_THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    /**
     * 线程的名字前缀
     */
    private static final String THREAD_NAME_PREFIX = "z-pool-";
    /**
     * 分发线程的名字前缀
     */
    private static final String DISTRIBUTOR_THREAD_NAME_PREFIX = "z-d-pool-";
    /**
     * 磁盘缓存目录
     */
    private static final String DISK_CACHE_DIR = "zImage";
    /**
     * 磁盘缓存大小
     */
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 100;
    /**
     * 内存缓存大小
     */
    private static final int MEMORY_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 8);

    /**
     * 创建线程池
     * @return
     */
    public static Executor createExecutor() {
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
        return new ThreadPoolExecutor(DEFAULT_THREAD_POOL_SIZE, DEFAULT_THREAD_POOL_SIZE, 0L, TimeUnit.MILLISECONDS, taskQueue,
                createThreadFactory(DEFAULT_THREAD_PRIORITY, THREAD_NAME_PREFIX));
    }

    /**
     * 创建任务分发线程池
     */
    public static Executor createTaskDistributor() {
        return Executors.newCachedThreadPool(createThreadFactory(Thread.NORM_PRIORITY, DISTRIBUTOR_THREAD_NAME_PREFIX));
    }

    /**
     * 创建磁盘缓存器
     * @return
     */
    public static LruDiskCache createDiskCache() {
        File cacheDir = createDiskCacheDir();
        LruDiskCache diskCache = new LruDiskCache(cacheDir, DISK_CACHE_SIZE);
        return diskCache;
    }

    /**
     * 创建磁盘缓存目录
     */
    private static File createDiskCacheDir() {
        File cacheDir = new File(Environment.getExternalStorageDirectory(), DISK_CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
        return cacheDir;
    }

    /**
     * 创建内存缓存器
     * @param context
     * @return
     */
    public static LruMemoryCache createMemoryCache(Context context) {
        return new LruMemoryCache(context, MEMORY_CACHE_SIZE);
    }

    /**
     * Creates default implementation of {@link BaseDownloader} - {@link ImageDownloader}
     */
    public static BaseDownloader createImageDownloader(Context context) {
        return new ImageDownloader(context);
    }

    public static BaseDownloader createNetworkDeniedDownloader(BaseDownloader downloader) {
        return new NetworkDeniedImageDownloader(downloader);
    }

    public static BaseDownloader createSlowNetworkDownloader(BaseDownloader downloader) {
        return new SlowNetworkImageDownloader(downloader);
    }

    /**
     * Creates default implementation of {@link BaseDecoder} - {@link ImageDecoder}
     */
    public static BaseDecoder createImageDecoder() {
        return new ImageDecoder();
    }

    /**
     * Creates default implementation of {@linkplain ThreadFactory thread factory} for task executor
     */
    private static ThreadFactory createThreadFactory(int threadPriority, String threadNamePrefix) {
        return new DefaultThreadFactory(threadPriority, threadNamePrefix);
    }

    private static class DefaultThreadFactory implements ThreadFactory {

        private static final AtomicInteger poolNumber = new AtomicInteger(1);

        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int threadPriority;

        DefaultThreadFactory(int threadPriority, String threadNamePrefix) {
            this.threadPriority = threadPriority;
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = threadNamePrefix + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) t.setDaemon(false);
            t.setPriority(threadPriority);
            return t;
        }
    }
}
