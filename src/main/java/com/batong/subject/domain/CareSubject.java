package com.batong.subject.domain;

import com.batong.global.config.JacksonConfig;
import com.batong.global.enums.District;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 돌봄 대상자 (ERD 3-1 care_subject). 나이는 저장하지 않고 birthYear로 계산한다 */
@Getter
@Entity
@Table(name = "care_subject")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSubject {

    static final int NAME_MIN = 2;
    static final int NAME_MAX = 20;
    static final int BIRTH_YEAR_MIN = 1930;
    static final int DONG_MAX = 20;
    static final int EMERGENCY_CONTACT_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subject_id")
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 20)
    private String name;

    /** DB가 SMALLINT라 ddl-auto=validate에 맞춰 Short */
    @Column(name = "birth_year", nullable = false)
    private Short birthYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private District district;

    @Column(length = 20)
    private String dong;

    /** LLM에 보내지 않는다 (AI 명세 2-2) */
    @Column(name = "emergency_contact", length = 100)
    private String emergencyContact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 올해(KST) - 출생 연도 */
    public int age() {
        return currentYear() - birthYear;
    }

    /**
     * SUBJ-03. null인 값은 바꾸지 않는다. dong·emergencyContact는 빈 문자열이면 지운다.
     * 하나라도 바뀌면 updatedAt을 갱신한다.
     */
    public void update(String name, Integer birthYear, District district, String dong, String emergencyContact) {
        if (name != null) {
            validateName(name);
        }
        if (birthYear != null) {
            validateBirthYear(birthYear);
        }
        String newDong = dong == null ? this.dong : blankToNull(dong, DONG_MAX);
        String newContact = emergencyContact == null ? this.emergencyContact
                : blankToNull(emergencyContact, EMERGENCY_CONTACT_MAX);

        boolean changed = false;
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            changed = true;
        }
        if (birthYear != null && birthYear != this.birthYear.intValue()) {
            this.birthYear = birthYear.shortValue();
            changed = true;
        }
        if (district != null && district != this.district) {
            this.district = district;
            changed = true;
        }
        if (!Objects.equals(newDong, this.dong)) {
            this.dong = newDong;
            changed = true;
        }
        if (!Objects.equals(newContact, this.emergencyContact)) {
            this.emergencyContact = newContact;
            changed = true;
        }
        if (changed) {
            this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    /** 이름 2~20자 (ERD CHECK. 2자 이상은 LLM 마스킹 때문). 저장할 수 없는 문자는 COMMON_VALIDATION */
    public static void validateName(String name) {
        requireStorable(name);
        int length = name.codePointCount(0, name.length());
        if (name.isBlank() || length < NAME_MIN || length > NAME_MAX) {
            throw new BusinessException(ErrorCode.SUBJECT_INVALID_NAME);
        }
    }

    /** 출생 연도 1930~올해(KST) (ERD CHECK) */
    public static void validateBirthYear(int birthYear) {
        if (birthYear < BIRTH_YEAR_MIN || birthYear > currentYear()) {
            throw new BusinessException(ErrorCode.SUBJECT_INVALID_BIRTH_YEAR);
        }
    }

    private static String blankToNull(String value, int maxLength) {
        if (value.isBlank()) {
            return null;
        }
        requireStorable(value);
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
        return value;
    }

    /** PostgreSQL은 NUL을 저장하지 못하고(500), 짝 없는 서로게이트는 저장할 때 조용히 '?'로 바뀐다(#65와 같은 규칙) */
    private static void requireStorable(String value) {
        if (value.indexOf('\u0000') >= 0 || value.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION);
        }
    }

    private static int currentYear() {
        return LocalDate.now(JacksonConfig.KST).getYear();
    }
}
