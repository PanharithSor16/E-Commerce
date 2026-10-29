package com.org.ecommerce.mapper;

import java.util.List;

import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.Address;
import com.org.ecommerce.entity.CustomerOrder;
import com.org.ecommerce.entity.Enums.OrderStatus;
import com.org.ecommerce.entity.OrderItem;
import com.org.ecommerce.entity.Payment;
import com.org.ecommerce.entity.Refund;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderDtos.OrderSummary toSummary(CustomerOrder o, boolean includeCustomer) {
        int count = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderDtos.OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentStatus(),
                o.getGrandTotal(), o.getRefundedTotal(), o.getCurrency(), count, o.getPlacedAt(),
                includeCustomer ? o.getUser().getEmail() : null, includeCustomer ? o.getUser().getFullName() : null);
    }

    public static OrderDtos.OrderDetail toDetail(CustomerOrder o, List<Payment> payments, List<Refund> refunds,
                                                 boolean includeCustomer) {
        return new OrderDtos.OrderDetail(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentStatus(),
                o.getCurrency(), o.getSubtotal(), o.getDiscountTotal(), o.getShippingTotal(), o.getTaxTotal(),
                o.getGrandTotal(), o.getRefundedTotal(), o.getCouponCode(), o.getShippingMethodName(), o.getCustomerNote(),
                o.getItems().stream().map(i -> new OrderDtos.OrderItemResponse(i.getId(), i.getProduct().getId(),
                        i.getVariant().getId(), i.getProductName(), i.getVariantDescription(), i.getSku(), i.getUnitPrice(),
                        i.getQuantity(), i.getDiscountAmount(), i.getLineTotal())).toList(),
                o.getAddresses().stream().map(a -> new OrderDtos.OrderAddressResponse(a.getAddressType().name(),
                        a.getRecipientName(), a.getPhoneNumber(), a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                        a.getPostalCode(), a.getCountryCode())).toList(),
                o.getStatusHistory().stream().map(h -> new OrderDtos.StatusHistoryResponse(h.getStatusType().name(),
                        h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt())).toList(),
                payments.stream().map(OrderMapper::toPayment).toList(),
                refunds.stream().map(r -> new OrderDtos.RefundResponse(r.getId(), r.getAmount(), r.getReason(),
                        r.getStatus().name(), r.getCreatedAt(), r.getCompletedAt())).toList(),
                o.getPlacedAt(), o.getPaidAt(), o.getShippedAt(), o.getDeliveredAt(), o.getCancelledAt(),
                o.getCancelReason(), isCustomerCancellable(o),
                includeCustomer ? o.getUser().getEmail() : null, includeCustomer ? o.getUser().getFullName() : null);
    }

    public static OrderDtos.PaymentResponse toPayment(Payment p) {
        return new OrderDtos.PaymentResponse(p.getId(), p.getProvider(), p.getStatus().name(), p.getAmount(),
                p.getCurrency(), p.getCardBrand(), p.getCardLast4(), p.getFailureMessage(), p.getCreatedAt(),
                p.getCompletedAt());
    }

    public static OrderDtos.AddressResponse toAddress(Address a) {
        return new OrderDtos.AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhoneNumber(),
                a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(), a.getPostalCode(), a.getCountryCode(),
                a.isDefaultShipping(), a.isDefaultBilling());
    }

    /** Business rule: customers may cancel until the order is being processed. */
    public static boolean isCustomerCancellable(CustomerOrder o) {
        return o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED;
    }
}
