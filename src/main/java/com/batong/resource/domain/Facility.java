package com.batong.resource.domain;

import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import com.batong.global.enums.ResourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 복지시설 (ERD facility). (source, external_id)로 upsert한다 */
@Getter
@Entity
@Table(name = "facility",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}))
@PrimaryKeyJoinColumn(name = "resource_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility extends Resource {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilitySource source;

    @Column(name = "external_id", nullable = false, length = 50)
    private String externalId;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private District district;

    @Column(length = 200)
    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(columnDefinition = "text")
    private String services;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public static Facility create(FacilitySource source, String externalId, String name, String phone,
                                  String officialUrl, String facilityType, District district, String address,
                                  BigDecimal lat, BigDecimal lng, String services, Instant fetchedAt) {
        Facility f = new Facility();
        f.initBase(ResourceKind.FACILITY, name, phone, officialUrl);
        f.source = source;
        f.externalId = externalId;
        f.refresh(name, phone, officialUrl, facilityType, district, address, lat, lng, services, fetchedAt);
        return f;
    }

    /** 재적재 시 같은 (source, external_id) 시설의 값을 최신으로 덮어쓴다 */
    public void refresh(String name, String phone, String officialUrl, String facilityType, District district,
                        String address, BigDecimal lat, BigDecimal lng, String services, Instant fetchedAt) {
        updateBase(name, phone, officialUrl);
        this.facilityType = facilityType;
        this.district = district;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.services = services;
        this.fetchedAt = fetchedAt;
    }
}
