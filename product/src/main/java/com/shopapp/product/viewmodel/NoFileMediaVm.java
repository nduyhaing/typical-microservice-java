package com.shopapp.product.viewmodel;

public record NoFileMediaVm(Long id, String caption, String fileName, String mediaType, String url) {

    public static NoFileMediaVm empty(Long id) {
        return new NoFileMediaVm(id, "", "", "", "");
    }
}
