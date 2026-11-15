package com.shopapp.payment.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.exception.ResourceExistedException;
import com.shopapp.payment.model.PaymentProvider;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.repository.PaymentProviderRepository;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.provider.PaymentProviderAdminVm;
import com.shopapp.payment.viewmodel.provider.PaymentProviderReqVm;
import com.shopapp.payment.viewmodel.provider.PaymentProviderVm;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment providers (YAS {@code PaymentProviderService}, without mappers and media). A provider is offered to
 * customers when the admin enabled it, the payment service has a handler for it (COD, PAYPAL) and, for PayPal,
 * payment-paypal reports its credentials configured (decisions.md 5-16).
 */
@Service
@RequiredArgsConstructor
public class PaymentProviderService {

    /** Methods the payment service can take: COD (admin-confirmed) and PayPal; BANKING has no handler. */
    static final Set<String> HANDLED = Set.of(PaymentMethod.COD.name(), PaymentMethod.PAYPAL.name());

    private final PaymentProviderRepository repository;
    private final PaypalClient paypalClient;

    /** Enabled and available providers, by id (YAS took a Pageable for a handful of rows; not here). */
    @Transactional(readOnly = true)
    public List<PaymentProviderVm> getAvailableProviders() {
        return repository.findByEnabledTrueOrderByIdAsc().stream()
            .filter(this::available)
            .map(PaymentProviderVm::fromModel)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentProviderAdminVm> getAllProviders() {
        return repository.findAllByOrderByIdAsc().stream()
            .map(provider -> PaymentProviderAdminVm.fromModel(provider, available(provider)))
            .toList();
    }

    @Transactional
    public PaymentProviderAdminVm create(PaymentProviderReqVm request) {
        if (repository.existsById(request.id())) {
            throw new ResourceExistedException(Constants.ErrorCode.PAYMENT_PROVIDER_ALREADY_EXISTS, request.id());
        }
        PaymentProvider provider = new PaymentProvider();
        provider.setId(request.id());
        provider.setNew(true);
        apply(provider, request);
        PaymentProvider saved = repository.saveAndFlush(provider);
        return PaymentProviderAdminVm.fromModel(saved, available(saved));
    }

    @Transactional
    public PaymentProviderAdminVm update(PaymentProviderReqVm request) {
        PaymentProvider provider = repository.findById(request.id())
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PAYMENT_PROVIDER_NOT_FOUND, request.id()));
        apply(provider, request);
        PaymentProvider saved = repository.saveAndFlush(provider);
        return PaymentProviderAdminVm.fromModel(saved, available(saved));
    }

    /**
     * The method may be used right now.
     *
     * @throws BadRequestException when the provider is unknown, disabled or has no handler (PayPal without
     *     credentials is left to payment-paypal, which answers 503 "PayPal is not configured")
     */
    @Transactional(readOnly = true)
    public void checkEnabled(PaymentMethod method) {
        PaymentProvider provider = repository.findById(method.name()).orElse(null);
        if (provider == null || !provider.isEnabled() || !HANDLED.contains(provider.getId())) {
            throw new BadRequestException(Constants.ErrorCode.PAYMENT_METHOD_NOT_AVAILABLE, method);
        }
    }

    private boolean available(PaymentProvider provider) {
        if (!provider.isEnabled() || !HANDLED.contains(provider.getId())) {
            return false;
        }
        return !PaymentMethod.PAYPAL.name().equals(provider.getId()) || paypalClient.configured();
    }

    private static void apply(PaymentProvider provider, PaymentProviderReqVm request) {
        provider.setEnabled(request.enabled());
        provider.setName(request.name().trim());
        provider.setConfigureUrl(request.configureUrl());
        provider.setLandingViewComponentName(request.landingViewComponentName());
        provider.setAdditionalSettings(request.additionalSettings());
        provider.setMediaId(request.mediaId());
    }
}
