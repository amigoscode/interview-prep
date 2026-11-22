package com.amigoscode.restapi.order;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customer;

    @Enumerated(EnumType.STRING)
    private Product product;

    private int quantity;

    /** Worked out by the server: unit price times quantity. */
    private BigDecimal total;

    /** Changed by the server only: by paying, shipping or cancelling. */
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    private String shippingAddress;

    /** Optional. A message from the customer to the courier. */
    private String notes;

    /** Optional. */
    private String contactPhone;

    /** Written by staff, for staff. Customers must never see it. */
    private String internalNote;

    public Order() {
    }

    public Order(String customer, Product product, int quantity, String shippingAddress) {
        this.customer = customer;
        this.product = product;
        this.quantity = quantity;
        this.shippingAddress = shippingAddress;
        recalculateTotal();
    }

    /** Unit price times quantity. */
    public void recalculateTotal() {
        this.total = product.price().multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * PENDING and PAID orders can be cancelled. Cancelling a CANCELLED order changes nothing, so a
     * client that sends "cancel" twice is safe. A SHIPPED order has left the warehouse: too late.
     */
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new OrderStateException("Order " + id + " is SHIPPED and cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }
}
