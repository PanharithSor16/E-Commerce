package com.org.ecommerce.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.dto.auth.AuthDtos;
import com.org.ecommerce.dto.order.OrderDtos;
import com.org.ecommerce.entity.Address;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.AddressRepository;
import com.org.ecommerce.repository.UserRepository;

/** Self-service profile and address book. Every method is scoped to the caller's own user id. */
@Service
@Transactional
public class AccountService {

    private static final int MAX_ADDRESSES = 20;

    private final UserRepository users;
    private final AddressRepository addresses;
    private final AuditService audit;

    public AccountService(UserRepository users, AddressRepository addresses, AuditService audit) {
        this.users = users;
        this.addresses = addresses;
        this.audit = audit;
    }

    public User updateProfile(Long userId, AuthDtos.UpdateProfileRequest req) {
        User u = users.findWithRolesById(userId).orElseThrow(() -> ApiException.notFound("User"));
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setPhoneNumber(req.phoneNumber() == null || req.phoneNumber().isBlank() ? null : req.phoneNumber().trim());
        audit.record("PROFILE_UPDATED", "User", userId, Map.of());
        return u;
    }

    @Transactional(readOnly = true)
    public List<Address> addresses(Long userId) {
        return addresses.findByUserIdOrderByIdAsc(userId);
    }

    public Address addAddress(Long userId, OrderDtos.SavedAddressRequest req) {
        if (addresses.findByUserIdOrderByIdAsc(userId).size() >= MAX_ADDRESSES) {
            throw ApiException.unprocessable("TOO_MANY_ADDRESSES", "You can save at most " + MAX_ADDRESSES + " addresses.");
        }
        Address a = new Address();
        a.setUser(users.getReferenceById(userId));
        apply(a, req, userId);
        return addresses.save(a);
    }

    public Address updateAddress(Long userId, Long addressId, OrderDtos.SavedAddressRequest req) {
        Address a = addresses.findByIdAndUserId(addressId, userId).orElseThrow(() -> ApiException.notFound("Address"));
        apply(a, req, userId);
        return a;
    }

    public void deleteAddress(Long userId, Long addressId) {
        Address a = addresses.findByIdAndUserId(addressId, userId).orElseThrow(() -> ApiException.notFound("Address"));
        addresses.delete(a);
    }

    private void apply(Address a, OrderDtos.SavedAddressRequest req, Long userId) {
        OrderDtos.AddressInput in = req.address();
        if (req.defaultShipping()) {
            addresses.clearDefaultShipping(userId);
        }
        if (req.defaultBilling()) {
            addresses.clearDefaultBilling(userId);
        }
        a.setLabel(req.label());
        a.setRecipientName(in.recipientName().trim());
        a.setPhoneNumber(in.phoneNumber());
        a.setLine1(in.line1().trim());
        a.setLine2(in.line2());
        a.setCity(in.city().trim());
        a.setRegion(in.region());
        a.setPostalCode(in.postalCode());
        a.setCountryCode(in.countryCode().toUpperCase());
        a.setDefaultShipping(req.defaultShipping());
        a.setDefaultBilling(req.defaultBilling());
    }
}
