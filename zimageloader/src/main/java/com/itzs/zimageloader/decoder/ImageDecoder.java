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
package com.itzs.zimageloader.decoder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.itzs.zimageloader.IoUtils;
import com.itzs.zimageloader.downloader.BaseDownloader;
import com.itzs.zimageloader.view.ImageViewAware;

import java.io.IOException;
import java.io.InputStream;


public class ImageDecoder implements BaseDecoder {

    private static final String TAG = ImageDecoder.class.getSimpleName();

    protected static final String LOG_SUBSAMPLE_IMAGE = "Subsample original image (%1$s) to %2$s (scale = %3$d) [%4$s]";
    protected static final String LOG_SCALE_IMAGE = "Scale subsampled image (%1$s) to %2$s (scale = %3$.5f) [%4$s]";
    protected static final String LOG_ROTATE_IMAGE = "Rotate image on %1$d\u00B0 [%2$s]";
    protected static final String LOG_FLIP_IMAGE = "Flip image horizontally [%s]";
    protected static final String ERROR_CANT_DECODE_IMAGE = "Image can't be decoded [%s]";


    @Override
    public Bitmap decode(String uri, ImageViewAware imageAware, BaseDownloader downloader, Object extraForDownloader) throws IOException {
        Bitmap decodedBitmap;

        InputStream imageStream = getImageStream(uri, downloader, extraForDownloader);
        try {
            Log.d(TAG, "decode-uri-->" + uri);
            if (imageAware.isShouldCompress()) {
                /*允许压缩图片*/
                int[] imageSize = defineImageSize(imageStream);
                Log.d(TAG, "decode-imageSize-->width:" + imageSize[0] + " height:" + imageSize[1]);
                imageStream = resetStream(imageStream, uri, downloader, extraForDownloader);
                Log.d(TAG, "decode-targetSize-->width:" + imageAware.getTargetSize()[0] + " height:" + imageAware.getTargetSize()[1]);
                Options decodingOptions = prepareDecodingOptions(imageSize, imageAware.getTargetSize());
                Log.d(TAG, "decode-scale-->" + decodingOptions.inSampleSize);
                decodedBitmap = BitmapFactory.decodeStream(imageStream, null, decodingOptions);
            } else {
                decodedBitmap = BitmapFactory.decodeStream(imageStream, null, null);
            }
//			imageInfo = defineImageSizeAndRotation(imageStream, decodingInfo);
//			imageStream = resetStream(imageStream, decodingInfo);
//			Options decodingOptions = prepareDecodingOptions(imageInfo.imageSize, decodingInfo);
        } finally {
            IoUtils.closeSilently(imageStream);
        }

        if (decodedBitmap == null) {
            Log.e(TAG, ERROR_CANT_DECODE_IMAGE + "-->" + uri);
        } else {
//			decodedBitmap = considerExactScaleAndOrientatiton(decodedBitmap, decodingInfo, imageInfo.exif.rotation,
//					imageInfo.exif.flipHorizontal);
        }
        return decodedBitmap;
    }

    protected InputStream getImageStream(String uri, BaseDownloader downloader, Object extraForDownloader) throws IOException {
        return downloader.getStream(uri, extraForDownloader);
    }

    /**
     * 计算并返回图片的实际宽高
     *
     * @param imageStream
     * @return int[] imageSize；imageSize[0]宽，imageSize[1]高
     * @throws IOException
     */
    protected int[] defineImageSize(InputStream imageStream)
            throws IOException {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(imageStream, null, options);
        int[] imageSize = {options.outWidth, options.outHeight};
        return imageSize;
    }

    protected Options prepareDecodingOptions(int[] imageSize, int[] targetSize) {

        int scale = computeImageSampleSize(imageSize[0], imageSize[1], targetSize[0], targetSize[1], true, true);

        Options decodingOptions = new Options();
        decodingOptions.inSampleSize = scale;
        return decodingOptions;
    }

    protected InputStream resetStream(InputStream imageStream, String uri, BaseDownloader downloader, Object extraForDownloader) throws IOException {
        try {
            imageStream.reset();
        } catch (IOException e) {
            IoUtils.closeSilently(imageStream);
            imageStream = getImageStream(uri, downloader, extraForDownloader);
        }
        return imageStream;
    }

    public static int computeImageSampleSize(int srcWidth, int srcHeight, int targetWidth, int targetHeight,
                                             boolean isQualityPriority,
                                             boolean powerOf2Scale) {
        int scale = 1;

        if (isQualityPriority) {
            if (powerOf2Scale) {
                final int halfWidth = srcWidth / 2;
                final int halfHeight = srcHeight / 2;
                while ((halfWidth / scale) > targetWidth && (halfHeight / scale) > targetHeight) { // &&
                    scale *= 2;
                }
            } else {
                scale = Math.min(srcWidth / targetWidth, srcHeight / targetHeight); // min
            }
        } else {
            if (powerOf2Scale) {
                final int halfWidth = srcWidth / 2;
                final int halfHeight = srcHeight / 2;
                while ((halfWidth / scale) > targetWidth || (halfHeight / scale) > targetHeight) { // ||
                    scale *= 2;
                }
            } else {
                scale = Math.max(srcWidth / targetWidth, srcHeight / targetHeight); // max
            }
        }

        if (scale < 1) {
            scale = 1;
        }

        return scale;
    }
}