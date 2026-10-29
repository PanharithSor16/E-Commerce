package com.org.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.org.ecommerce.entity.Enums;
import com.org.ecommerce.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    List<Payment> findByCustomerOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findFirstByCustomerOrderIdAndStatus(Long orderId, Enums.PaymentTxStatus status);

    Optional<Payment> findFirstByCustomerOrderIdOrderByIdDesc(Long orderId);
}
