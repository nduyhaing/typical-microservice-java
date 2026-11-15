package com.shopapp.product.viewmodel;

import java.util.Map;

public record ImageVm(Long id, String url) {

    /** This image with its URL from a {@code MediaService#getMediaUrls} result; empty when unknown. */
    public ImageVm withUrlFrom(Map<Long, String> urls) {
        return new ImageVm(id, id == null ? "" : urls.getOrDefault(id, ""));
    }

    /** Null-safe {@link #withUrlFrom(Map)}. */
    public static ImageVm withUrlFrom(ImageVm image, Map<Long, String> urls) {
        return image == null ? null : image.withUrlFrom(urls);
    }
}
