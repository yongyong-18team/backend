package com.batong.resource.dto;

public final class PublicDataDtos {

    private PublicDataDtos() {
    }

    // ADMIN-01. 응답 data 모양은 명세에 없어서 적재 결과 건수로 정했다
    public record SyncResponse(int total, int created, int updated) {
    }
}
