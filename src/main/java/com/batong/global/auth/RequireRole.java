package com.batong.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /subjects/{subjectId}/** 컨트롤러 메서드에 붙여 허용할 역할을 적는다. 경로 변수 이름은 반드시 {@code subjectId}.
 * 지금은 표시만 하는 껍데기다. 역할 판정 인터셉터는 #5에서 붙는다. 서비스 계층 확인은 그와 별개로 각자 한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    SubjectRole[] value();
}
