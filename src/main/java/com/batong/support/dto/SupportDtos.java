package com.batong.support.dto;

import com.batong.global.enums.ProgramType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record BurnoutGuideResponse(List<String> message, List<ProgramSummary> programs) {
    }

    public record ProgramSummary(Long resourceId, String name, ProgramType programType) {
    }

    public record CrisisGuideResponse(List<String> message, List<Contact> contacts, FollowUp followUp) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Contact(String name, String phone, String hours, String note) {
    }

    public record FollowUp(Long resourceId, String name) {
    }
}
