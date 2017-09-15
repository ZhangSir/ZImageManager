package com.itzs.zimageloader.downloader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decorator. Prevents downloads from network (throws {@link IllegalStateException exception}).<br />
 * In most cases this downloader shouldn't be used directly.
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.8.0
 */
public class NetworkDeniedImageDownloader implements BaseDownloader {

    private final BaseDownloader wrappedDownloader;

    public NetworkDeniedImageDownloader(BaseDownloader wrappedDownloader) {
        this.wrappedDownloader = wrappedDownloader;
    }

    @Override
    public InputStream getStream(String imageUri, Object extra) throws IOException {
        switch (Scheme.ofUri(imageUri)) {
            case HTTP:
            case HTTPS:
                throw new IllegalStateException();
            default:
                return wrappedDownloader.getStream(imageUri, extra);
        }
    }
}
