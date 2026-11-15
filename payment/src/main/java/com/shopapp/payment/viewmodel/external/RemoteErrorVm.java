package com.shopapp.payment.viewmodel.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Another shop_app service's ErrorVm. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteErrorVm(String statusCode, String title, String detail) {
}
