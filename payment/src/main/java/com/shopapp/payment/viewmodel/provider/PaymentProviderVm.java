package com.shopapp.payment.viewmodel.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shopapp.payment.model.PaymentProvider;

/** YAS {@code PaymentProviderVm} (storefront list); no icon URL (shop_app has no provider media). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentProviderVm(String id, String name, String configureUrl, int version, Long mediaId,
                                String iconUrl) {

    public static PaymentProviderVm fromModel(PaymentProvider provider) {
        return new PaymentProviderVm(provider.getId(), provider.getName(), provider.getConfigureUrl(),
            provider.getVersion(), provider.getMediaId(), null);
    }
}
