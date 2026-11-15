package com.shopapp.search.viewmodel;

import java.util.List;

/** Search suggestions (YAS ProductNameListVm / ProductNameGetVm). */
public record ProductNameListVm(List<ProductNameGetVm> productNames) {

    /** One suggested product name. */
    public record ProductNameGetVm(String name) {
    }
}
