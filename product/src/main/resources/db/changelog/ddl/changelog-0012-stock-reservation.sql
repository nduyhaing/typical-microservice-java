--liquibase formatted sql

--changeset shopapp:product-0012-stock-reservation
-- Units taken by subtract-quantity under a reference, given back once by restore-quantity (decisions.md 3c-22).
create table stock_reservation
(
    id         bigserial                   not null,
    reference  varchar(64)                 not null,
    product_id bigint                      not null,
    quantity   bigint                      not null,
    owner      varchar(255)                not null,
    created_on timestamp(6) with time zone not null,
    constraint stock_reservation_pkey primary key (id),
    constraint stock_reservation_product_fk foreign key (product_id) references product (id),
    constraint stock_reservation_reference_product_key unique (reference, product_id),
    constraint stock_reservation_quantity_check check (quantity > 0)
);
