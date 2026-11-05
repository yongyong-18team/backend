package com.batong.user.dto;

import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.Relation;

import java.time.LocalDate;
import java.util.List;

/** USER-01~02, HOME-01 요청·응답 */
public final class UserDtos {

    private UserDtos() {
    }

    public record MeResponse(Long userId, String loginId, String displayName, Relation relation,
                             List<SubjectSummary> subjects, int pendingInvitationCount) {
    }

    public record ProfileUpdateRequest(String displayName, Relation relation) {
    }

    public record ProfileUpdateResponse(Long userId, String displayName, Relation relation) {
    }

    /** preview는 CO에게 null(일기 원문 비공개) */
    public record HomeResponse(SubjectRole role, String name, boolean todayRecorded,
                               List<RecentActivity> recentActivities, int pendingInvitationCount) {
    }

    public record RecentActivity(Long diaryId, LocalDate diaryDate, String preview, int approvedCount, int pendingCount) {
    }
}
