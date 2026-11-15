package com.shopapp.payment.model.enumeration;

/** YAS payment methods; BANKING has no handler in YAS or shop_app and is refused by {@code /init}. */
public enum PaymentMethod {
    COD, BANKING, PAYPAL
}
