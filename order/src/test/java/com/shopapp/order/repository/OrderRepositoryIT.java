package com.shopapp.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.order.PostgresContainerSupport;
import com.shopapp.order.model.Order;
import com.shopapp.order.model.OrderAddress;
import com.shopapp.order.model.OrderItem;
import com.shopapp.order.model.enumeration.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresContainerSupport.class)
class OrderRepositoryIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    void savesOrderWithAddressAndItems() {
        OrderAddress address = OrderAddress.builder().contactName("Customer Dev").phone("0123").addressLine1("1 Main St")
            .city("Ho Chi Minh City").countryName("Vietnam").build();
        Order order = orderRepository.saveAndFlush(Order.builder().email("customer@shopapp.local")
            .shippingAddressId(address).billingAddressId(address).totalPrice(new BigDecimal("819.00"))
            .orderStatus(OrderStatus.PENDING_PAYMENT).checkoutId("c-1").numberItem(1).build());
        orderItemRepository.saveAndFlush(OrderItem.builder().orderId(order.getId()).productId(25L)
            .productName("Galaxy S24 Violet").quantity(1).productPrice(new BigDecimal("819.00")).build());

        assertThat(orderRepository.findByCheckoutId("c-1")).get()
            .extracting(Order::getOrderStatus).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderItemRepository.findAllByOrderId(order.getId())).extracting(OrderItem::getProductName)
            .containsExactly("Galaxy S24 Violet");
    }
}
