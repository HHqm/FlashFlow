package com.flashflow.trade.address;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipping_address")
public class ShippingAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_name", nullable = false, length = 80)
    private String recipientName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 80)
    private String province;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(name = "detail_address", nullable = false, length = 240)
    private String detailAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "default_address", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShippingAddressEntity() {
    }

    public ShippingAddressEntity(long userId, AddressDtos.SaveAddressRequest request) {
        this.userId = userId;
        this.createdAt = Instant.now();
        update(request);
    }

    public void update(AddressDtos.SaveAddressRequest request) {
        recipientName = request.recipientName().trim();
        phone = request.phone().trim();
        province = request.province().trim();
        city = request.city().trim();
        district = request.district().trim();
        detailAddress = request.detailAddress().trim();
        postalCode = normalize(request.postalCode());
        defaultAddress = request.defaultAddress();
        updatedAt = Instant.now();
    }

    public void markDefault(boolean value) {
        defaultAddress = value;
        updatedAt = Instant.now();
    }

    public String fullAddress() {
        return fullAddress(province, city, district, detailAddress);
    }

    static String fullAddress(String province, String city, String district, String detailAddress) {
        return String.join(" ", province, city, district, detailAddress);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getDetailAddress() { return detailAddress; }
    public String getPostalCode() { return postalCode; }
    public boolean isDefaultAddress() { return defaultAddress; }
}
