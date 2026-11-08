package com.batong.resource.domain;

import com.batong.global.enums.ResourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 돌봄 자원 공통 부모 (ERD resource). 시설(facility)과 제도(program)가 JOINED로 상속한다 */
@Getter
@Entity
@Table(name = "resource")
@Inheritance(strategy = InheritanceType.JOINED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resource_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_kind", nullable = false, length = 10)
    private ResourceKind resourceKind;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "official_url", length = 300)
    private String officialUrl;

    protected void initBase(ResourceKind resourceKind, String name, String phone, String officialUrl) {
        this.resourceKind = resourceKind;
        updateBase(name, phone, officialUrl);
    }

    protected void updateBase(String name, String phone, String officialUrl) {
        this.name = name;
        this.phone = phone;
        this.officialUrl = officialUrl;
    }
}
