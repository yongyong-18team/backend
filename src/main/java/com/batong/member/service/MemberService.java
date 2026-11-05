package com.batong.member.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.InvitationStatus;
import com.batong.global.enums.Relation;
import com.batong.member.dto.MemberDtos.AcceptResponse;
import com.batong.member.dto.MemberDtos.CoManager;
import com.batong.member.dto.MemberDtos.InviteRequest;
import com.batong.member.dto.MemberDtos.InviteResponse;
import com.batong.member.dto.MemberDtos.LookupResponse;
import com.batong.member.dto.MemberDtos.MembersResponse;
import com.batong.member.dto.MemberDtos.Owner;
import com.batong.member.dto.MemberDtos.ReceivedInvitation;
import com.batong.member.dto.MemberDtos.ReceivedInvitationsResponse;
import com.batong.member.dto.MemberDtos.RemoveResponse;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.batong.global.StubData.at;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #16~#18 */
@Service
public class MemberService {

    /** INVITE-04 고정 목록 (PRD 6.2.1) */
    public static final List<String> PERMISSIONS = List.of("돌봄 설명서 열람", "돌봄 정보 공유", "돌봄 지원 이용");
    public static final List<String> RESTRICTED_ITEMS = List.of("일기 원문", "설명서 수정", "보호자 상태 정보");

    public LookupResponse lookup(String loginId) {
        return new LookupResponse(2L, "이철수");
    }

    public InviteResponse invite(Long subjectId, Long userId, InviteRequest request) {
        return new InviteResponse(70L, InvitationStatus.PENDING);
    }

    public void cancelInvitation(Long subjectId, Long invitationId, Long userId) {
    }

    public ReceivedInvitationsResponse getReceivedInvitations(Long userId) {
        return new ReceivedInvitationsResponse(List.of(new ReceivedInvitation(70L, "김영희", "민지",
                PERMISSIONS, RESTRICTED_ITEMS, at("2026-11-03T20:00:00+09:00"))));
    }

    public AcceptResponse accept(Long invitationId, Long userId) {
        return new AcceptResponse(10L, SubjectRole.CO_MANAGER);
    }

    public void reject(Long invitationId, Long userId) {
    }

    public MembersResponse getMembers(Long subjectId, Long userId) {
        return new MembersResponse(new Owner(1L, "김영희", Relation.PARENT, "mom1234"),
                List.of(new CoManager(2L, "이철수", Relation.OTHER, "dad5678", at("2026-11-03T20:10:00+09:00"))));
    }

    public RemoveResponse removeMember(Long subjectId, Long memberUserId, Long userId) {
        return new RemoveResponse(2);
    }
}
