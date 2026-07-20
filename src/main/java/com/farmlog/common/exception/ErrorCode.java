package com.farmlog.common.exception;

import org.springframework.http.HttpStatus;

/**
 * plan/02_Development_Guide.md 4.3의 공통 에러 응답 규약({@code code}/{@code message}/{@code traceId})에서
 * 사용하는 에러 코드 목록. 프론트엔드는 code 값을 기준으로 처리를 분기한다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해주세요."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
    AUTH_EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다."),
    FARM_NOT_FOUND(HttpStatus.NOT_FOUND, "농장을 찾을 수 없습니다."),
    FARM_ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "하우스/구역을 찾을 수 없습니다."),
    CROP_NOT_FOUND(HttpStatus.NOT_FOUND, "작물 정보를 찾을 수 없습니다."),
    CROP_VARIETY_NOT_FOUND(HttpStatus.NOT_FOUND, "품종 정보를 찾을 수 없습니다."),
    CROP_SEASON_NOT_FOUND(HttpStatus.NOT_FOUND, "작기 정보를 찾을 수 없습니다."),
    WORK_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "작업유형을 찾을 수 없습니다."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "거래처를 찾을 수 없습니다."),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "자재를 찾을 수 없습니다."),
    WORK_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "작업 기록을 찾을 수 없습니다."),
    PEST_CONTROL_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "방제 기록을 찾을 수 없습니다."),
    HARVEST_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "수확 기록을 찾을 수 없습니다."),
    SALES_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 기록을 찾을 수 없습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."),
    ATTACHMENT_INVALID_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
    ATTACHMENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 용량이 너무 큽니다."),
    EXPORT_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "내보내기 작업을 찾을 수 없습니다."),
    EXPORT_NOT_READY(HttpStatus.CONFLICT, "파일을 아직 생성하고 있습니다."),
    EXPORT_FAILED(HttpStatus.CONFLICT, "파일 생성에 실패했습니다. 다시 요청해주세요."),
    EXPORT_EXPIRED(HttpStatus.GONE, "파일 보관 기간이 지나 만료되었습니다. 다시 만들어주세요."),
    EXPORT_FILE_MISSING(HttpStatus.GONE, "파일을 찾을 수 없습니다. 다시 만들어주세요."),
    EXPORT_ROW_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "기록이 너무 많습니다. 기간이나 포함 범위를 나눠주세요."),
    FARM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "농장 구성원을 찾을 수 없습니다."),
    FARM_INVITATION_INVALID(HttpStatus.BAD_REQUEST, "초대가 유효하지 않습니다."),
    FARM_INVITATION_EXPIRED(HttpStatus.GONE, "초대 유효기간이 만료되었습니다."),
    ATTACHMENT_NOT_READY(HttpStatus.CONFLICT, "사진 업로드가 아직 완료되지 않았습니다."),
    PHOTO_DRAFT_EXPIRED(HttpStatus.GONE, "사진 임시 저장 시간이 만료되었습니다."),
    CARE_ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "매니저 배정 정보를 찾을 수 없습니다."),
    DATA_QUALITY_ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "데이터 이슈를 찾을 수 없습니다."),
    FARM_STRUCTURE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "농장 구조 변경 이벤트를 찾을 수 없습니다."),
    FARM_READ_ONLY(HttpStatus.CONFLICT, "현재 농장은 구조 변경으로 읽기 전용입니다."),
    STRUCTURE_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 요청 ID에 다른 입력이 사용되었습니다."),
    EVENT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 구조 변경입니다."),
    PREVIEW_EXPIRED(HttpStatus.GONE, "영향 미리보기의 유효기간이 지났습니다."),
    PREVIEW_STALE(HttpStatus.CONFLICT, "농장 정보가 바뀌었습니다. 영향을 다시 확인해주세요."),
    PERIOD_CONFLICT(HttpStatus.CONFLICT, "구역 소속 기간이 다른 변경과 겹칩니다."),
    STRUCTURE_CANCEL_BLOCKED(HttpStatus.CONFLICT, "후속 변경이 있어 구조 변경을 취소할 수 없습니다."),
    FARM_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 정보를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청을 처리할 수 없는 상태입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
