package com.shopapp.commonlibrary.viewmodel.error;

import java.util.List;

public record ErrorVm(String statusCode, String title, String detail, List<String> fieldErrors) {

    public ErrorVm(String statusCode, String title, String detail) {
        this(statusCode, title, detail, List.of());
    }
}
