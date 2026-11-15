package com.shopapp.payment.viewmodel.provider;

import com.shopapp.payment.model.PaymentProvider;

/**
 * A payment provider in the back office: the admin's {@code enabled} switch and whether customers are offered it
 * right now ({@code available}: enabled, handled by the payment service, and for PayPal credentials configured).
 */
public record PaymentProviderAdminVm(String id, String name, boolean enabled, boolean available, String configureUrl,
                                     String landingViewComponentName, String additionalSettings, Long mediaId,
                                     int version) {

    public static PaymentProviderAdminVm fromModel(PaymentProvider provider, boolean available) {
        return new PaymentProviderAdminVm(provider.getId(), provider.getName(), provider.isEnabled(), available,
            provider.getConfigureUrl(), provider.getLandingViewComponentName(), provider.getAdditionalSettings(),
            provider.getMediaId(), provider.getVersion());
    }
}
