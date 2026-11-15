package com.shopapp.order.viewmodel.external;

import java.util.List;

/** The error body (ErrorVm) another shop_app service answers with. */
public record RemoteErrorVm(String statusCode, String title, String detail, List<String> fieldErrors) {
}
