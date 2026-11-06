package com.batong.member.dto;

import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.InvitationStatus;
import com.batong.global.enums.Relation;

import java.time.Instant;
import java.util.List;

/** MEMBER-01~03, INVITE-01·04~06 요청·응답 */
public final class MemberDtos {

    private MemberDtos() {
    }

    public record LookupResponse(Long userId, String displayName) {
    }

    public record InviteRequest(String inviteeLoginId) {
    }

    public record InviteResponse(Long invitationId, InvitationStatus status) {
    }

    /** 수락 전에는 대상자 표시 이름만. permissions·restrictedItems는 고정 목록 */
    public record ReceivedInvitationsResponse(List<ReceivedInvitation> invitations) {
    }

    public record ReceivedInvitation(Long invitationId, String inviterDisplayName, String subjectName,
                                     List<String> permissions, List<String> restrictedItems, Instant createdAt) {
    }

    public record AcceptResponse(Long subjectId, SubjectRole role) {
    }

    /** CO가 호출하면 loginId는 본인 것만 내려준다 */
    public record MembersResponse(Owner owner, List<CoManager> coManagers) {
    }

    public record Owner(Long userId, String displayName, Relation relation, String loginId) {
    }

    public record CoManager(Long userId, String displayName, Relation relation, String loginId, Instant activatedAt) {
    }

    public record RemoveResponse(int revokedShareCount) {
    }
}
