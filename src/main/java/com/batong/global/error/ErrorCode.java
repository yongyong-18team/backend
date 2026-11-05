package com.batong.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

/** API 명세 v1.4 4장 에러 코드 표와 같아야 한다. 코드를 추가하려면 명세부터 고친다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    COMMON_VALIDATION(BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    COMMON_UNAUTHORIZED(UNAUTHORIZED, "다시 로그인해 주세요."),
    COMMON_FORBIDDEN(FORBIDDEN, "이용할 수 있는 권한이 없어요."),
    COMMON_NOT_FOUND(NOT_FOUND, "정보를 찾을 수 없어요."),
    COMMON_TOO_MANY_REQUESTS(TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
    COMMON_INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."),

    AUTH_INVALID_LOGIN_ID_FORMAT(BAD_REQUEST, "아이디는 영문 소문자와 숫자로 4~20자예요."),
    AUTH_INVALID_PASSWORD_FORMAT(BAD_REQUEST, "비밀번호는 영문과 숫자를 넣어 8~20자로 만들어 주세요."),
    AUTH_DUPLICATE_LOGIN_ID(CONFLICT, "이미 사용 중인 아이디예요."),
    AUTH_INVALID_CREDENTIALS(UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요."),
    AUTH_TOO_MANY_ATTEMPTS(TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주세요."),

    SUBJECT_INVALID_NAME(BAD_REQUEST, "이름은 2~20자로 입력해 주세요."),
    SUBJECT_INVALID_BIRTH_YEAR(BAD_REQUEST, "태어난 연도를 다시 확인해 주세요."),
    SUBJECT_CONSENT_REQUIRED(BAD_REQUEST, "필수 동의 항목에 동의해 주세요."),
    ONB_ALREADY_COMPLETED(CONFLICT, "이미 등록을 마쳤어요."),

    STT_FILE_TOO_LARGE(PAYLOAD_TOO_LARGE, "녹음이 너무 길어요. 3분 이내로 나눠서 녹음해 주세요."),
    STT_UNSUPPORTED_FORMAT(BAD_REQUEST, "녹음 파일을 읽을 수 없어요."),
    STT_FAILED(BAD_GATEWAY, "음성을 글로 바꾸지 못했어요. 다시 시도하거나 글로 입력해 주세요."),

    DIARY_EMPTY_CONTENT(BAD_REQUEST, "내용을 입력해 주세요."),
    DIARY_ALREADY_ANALYZING(CONFLICT, "분석 중이에요. 잠시만 기다려 주세요."),
    AI_CONSENT_REQUIRED(FORBIDDEN, "AI 분석 이용에 동의가 필요해요."),
    AI_TIMEOUT(BAD_GATEWAY, "분석이 늦어지고 있어요. 기록은 저장됐으니 다시 시도해 주세요."),

    GUIDE_ITEM_NOT_FOUND(NOT_FOUND, "항목을 찾을 수 없어요."),
    GUIDE_NOTHING_TO_APPLY(BAD_REQUEST, "반영할 항목을 하나 이상 선택해 주세요."),

    SHARE_NO_SECTION(BAD_REQUEST, "공개할 항목을 하나 이상 선택해 주세요."),
    SHARE_SOURCE_REQUIRED(BAD_REQUEST, "공유할 기록을 선택해 주세요."),
    SHARE_NO_APPROVED_CONTENT(BAD_REQUEST, "공유할 수 있는 승인된 정보가 없어요."),
    SHARE_ALREADY_APPROVED(CONFLICT, "이미 발급된 공유예요."),
    SHARE_ALREADY_INACTIVE(CONFLICT, "이미 만료되었거나 접근이 중지된 링크예요."),
    SHARE_FORBIDDEN(FORBIDDEN, "이 공유를 관리할 권한이 없어요."),
    SHARE_NOT_FOUND(NOT_FOUND, "링크를 찾을 수 없어요."),
    SHARE_EXPIRED(GONE, "유효기간이 지난 링크예요. 보호자에게 새 링크를 요청해 주세요."),
    SHARE_REVOKED(GONE, "더 이상 볼 수 없는 링크예요."),

    MEMBER_NOT_FOUND(NOT_FOUND, "공동 관리자를 찾을 수 없어요."),
    MEMBER_CANNOT_REMOVE_OWNER(BAD_REQUEST, "주 보호자는 삭제할 수 없어요."),
    INVITE_USER_NOT_FOUND(NOT_FOUND, "해당 아이디의 사용자가 없어요."),
    INVITE_SELF(BAD_REQUEST, "본인은 초대할 수 없어요."),
    INVITE_ALREADY_MEMBER(CONFLICT, "이미 공동 관리자예요."),
    INVITE_DUPLICATE_PENDING(CONFLICT, "이미 초대를 보냈어요. 수락을 기다려 주세요."),
    INVITE_NOT_PENDING(CONFLICT, "이미 처리되었거나 취소된 초대예요."),
    INVITE_FORBIDDEN(FORBIDDEN, "본인에게 온 초대가 아니에요."),

    RESOURCE_INVALID_CONDITION(BAD_REQUEST, "찾을 조건을 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String message;
}
