package com.shopapp.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.cart.PostgresContainerSupport;
import com.shopapp.cart.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerSupport.class)
@Transactional
class CartItemRepositoryIT {

    @Autowired
    private CartItemRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private void item(String customer, long product, int quantity) {
        repository.saveAndFlush(CartItem.builder().customerId(customer).productId(product).quantity(quantity).build());
    }

    @Test
    void findsOnlyTheCustomersItems() {
        item("u-1", 10L, 1);
        item("u-1", 11L, 2);
        item("u-2", 10L, 5);

        assertThat(repository.findByCustomerIdOrderByCreatedOnDesc("u-1")).extracting(CartItem::getProductId)
            .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void findByCustomerAndProduct() {
        item("u-1", 10L, 3);

        assertThat(repository.findByCustomerIdAndProductId("u-1", 10L)).get()
            .extracting(CartItem::getQuantity).isEqualTo(3);
        assertThat(repository.findByCustomerIdAndProductId("u-2", 10L)).isEmpty();
    }

    @Test
    void deleteByCustomerAndProduct() {
        item("u-1", 10L, 1);
        item("u-2", 10L, 1);

        repository.deleteByCustomerIdAndProductId("u-1", 10L);

        assertThat(repository.findAll()).extracting(CartItem::getCustomerId).containsExactly("u-2");
    }
}
