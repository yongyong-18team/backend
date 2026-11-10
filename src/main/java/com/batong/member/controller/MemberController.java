package com.batong.member.controller;

import com.batong.global.auth.RequireRole;
import com.batong.global.auth.SubjectRole;
import com.batong.global.response.ApiResponse;
import com.batong.member.dto.MemberDtos.AcceptResponse;
import com.batong.member.dto.MemberDtos.InviteRequest;
import com.batong.member.dto.MemberDtos.InviteResponse;
import com.batong.member.dto.MemberDtos.LookupResponse;
import com.batong.member.dto.MemberDtos.MembersResponse;
import com.batong.member.dto.MemberDtos.ReceivedInvitationsResponse;
import com.batong.member.dto.MemberDtos.RemoveResponse;
import com.batong.member.dto.MemberDtos.SentInvitationsResponse;
import com.batong.global.enums.InvitationStatus;
import com.batong.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공동 관리자")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "MEMBER-01 계정 존재 확인 (표시명만)")
    @GetMapping("/users/lookup")
    public ApiResponse<LookupResponse> lookup(@RequestParam String loginId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.lookup(loginId, userId));
    }

    @Operation(summary = "INVITE-01 초대 발송")
    @RequireRole(SubjectRole.OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/subjects/{subjectId}/invitations")
    public ApiResponse<InviteResponse> invite(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId,
                                              @RequestBody InviteRequest request) {
        return ApiResponse.ok(memberService.invite(subjectId, userId, request));
    }

    @Operation(summary = "INVITE-02 보낸 초대 목록", description = "status를 생략하면 전체. 명세에 응답 형식이 없어 INVITE-04와 같은 모양으로 맞췄다.")
    @RequireRole(SubjectRole.OWNER)
    @GetMapping("/subjects/{subjectId}/invitations")
    public ApiResponse<SentInvitationsResponse> getSentInvitations(@PathVariable Long subjectId,
                                                                   @RequestParam(required = false) InvitationStatus status,
                                                                   @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.getSentInvitations(subjectId, userId, status));
    }

    @Operation(summary = "INVITE-03 대기 중 초대 취소")
    @RequireRole(SubjectRole.OWNER)
    @PostMapping("/subjects/{subjectId}/invitations/{invitationId}/cancel")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long subjectId, @PathVariable Long invitationId,
                                              @AuthenticationPrincipal Long userId) {
        memberService.cancelInvitation(subjectId, invitationId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "INVITE-04 받은 초대 목록")
    @GetMapping("/users/me/invitations")
    public ApiResponse<ReceivedInvitationsResponse> getReceivedInvitations(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.getReceivedInvitations(userId));
    }

    @Operation(summary = "INVITE-05 수락")
    @PostMapping("/users/me/invitations/{invitationId}/accept")
    public ApiResponse<AcceptResponse> accept(@PathVariable Long invitationId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.accept(invitationId, userId));
    }

    @Operation(summary = "INVITE-06 거절")
    @PostMapping("/users/me/invitations/{invitationId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long invitationId, @AuthenticationPrincipal Long userId) {
        memberService.reject(invitationId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "MEMBER-02 공동 관리자 목록")
    @RequireRole({SubjectRole.OWNER, SubjectRole.CO_MANAGER})
    @GetMapping("/subjects/{subjectId}/members")
    public ApiResponse<MembersResponse> getMembers(@PathVariable Long subjectId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.getMembers(subjectId, userId));
    }

    @Operation(summary = "MEMBER-03 공동 관리자 삭제 + 그 사람이 만든 활성 링크 철회")
    @RequireRole(SubjectRole.OWNER)
    @DeleteMapping("/subjects/{subjectId}/members/{userId}")
    public ApiResponse<RemoveResponse> removeMember(@PathVariable Long subjectId,
                                                    @PathVariable("userId") Long memberUserId,
                                                    @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(memberService.removeMember(subjectId, memberUserId, userId));
    }
}
