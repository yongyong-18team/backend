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
import com.batong.member.dto.MemberDtos.SentInvitation;
import com.batong.member.dto.MemberDtos.SentInvitationsResponse;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** MEMBER-01~03, INVITE-01·03~06. INVITE-02(보낸 초대 목록)는 응답 형식이 명세에 없어 구현하지 않았다. */
@Service
public class MemberService {

    /** INVITE-04 고정 목록 (PRD 6.2.1) */
    public static final List<String> PERMISSIONS = List.of("돌봄 설명서 열람", "돌봄 정보 공유", "돌봄 지원 이용");
    public static final List<String> RESTRICTED_ITEMS = List.of("일기 원문", "설명서 수정", "보호자 상태 정보");

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");

    private final JdbcClient jdbc;
    private final SubjectRoleResolver roles;
    private final LookupLimiter lookupLimiter;

    public MemberService(JdbcClient jdbc, SubjectRoleResolver roles, LookupLimiter lookupLimiter) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.lookupLimiter = lookupLimiter;
    }

    public LookupResponse lookup(String loginId, Long userId) {
        if (!lookupLimiter.tryAcquire(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.COMMON_TOO_MANY_REQUESTS);
        }
        return findUser(loginId);
    }

    private LookupResponse findUser(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new BusinessException(ErrorCode.INVITE_USER_NOT_FOUND);
        }
        return jdbc.sql("SELECT user_id, display_name FROM users WHERE login_id = :l").param("l", loginId)
                .query((rs, n) -> new LookupResponse(rs.getLong("user_id"), rs.getString("display_name")))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.INVITE_USER_NOT_FOUND));
    }

    @Transactional
    public InviteResponse invite(Long subjectId, Long userId, InviteRequest request) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        LookupResponse invitee = findUser(request == null ? null : request.inviteeLoginId());
        if (invitee.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVITE_SELF);
        }
        boolean member = jdbc.sql("SELECT EXISTS (SELECT 1 FROM co_manager WHERE subject_id = :s AND user_id = :u)")
                .param("s", subjectId).param("u", invitee.userId()).query(Boolean.class).single();
        if (member) {
            throw new BusinessException(ErrorCode.INVITE_ALREADY_MEMBER);
        }
        try {
            Long id = jdbc.sql("""
                    INSERT INTO invitation (subject_id, invitee_user_id, status, created_at)
                    VALUES (:s, :u, 'PENDING', :now) RETURNING invitation_id""")
                    .param("s", subjectId).param("u", invitee.userId()).param("now", Timestamp.from(Instant.now()))
                    .query(Long.class).single();
            return new InviteResponse(id, InvitationStatus.PENDING);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.INVITE_DUPLICATE_PENDING);
        }
    }

    /** 이 대상자의 초대가 아니면 INVITE_FORBIDDEN(명세에 404가 없다) */
    @Transactional
    public void cancelInvitation(Long subjectId, Long invitationId, Long userId) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        boolean mine = jdbc.sql("SELECT EXISTS (SELECT 1 FROM invitation WHERE invitation_id = :i AND subject_id = :s)")
                .param("i", invitationId).param("s", subjectId).query(Boolean.class).single();
        if (!mine) {
            throw new BusinessException(ErrorCode.INVITE_FORBIDDEN);
        }
        respond(invitationId, InvitationStatus.CANCELED);
    }

    /** status가 없으면 전체. 최신순 */
    public SentInvitationsResponse getSentInvitations(Long subjectId, Long userId, InvitationStatus status) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        return new SentInvitationsResponse(jdbc.sql("""
                SELECT i.invitation_id, u.login_id, u.display_name, i.status, i.created_at, i.responded_at
                FROM invitation i JOIN users u ON u.user_id = i.invitee_user_id
                WHERE i.subject_id = :s AND (CAST(:st AS text) IS NULL OR i.status = :st)
                ORDER BY i.created_at DESC, i.invitation_id DESC""")
                .param("s", subjectId).param("st", status == null ? null : status.name())
                .query((rs, n) -> new SentInvitation(rs.getLong("invitation_id"), rs.getString("login_id"),
                        rs.getString("display_name"), InvitationStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("responded_at") == null ? null : rs.getTimestamp("responded_at").toInstant()))
                .list());
    }

    public ReceivedInvitationsResponse getReceivedInvitations(Long userId) {
        return new ReceivedInvitationsResponse(jdbc.sql("""
                SELECT i.invitation_id, o.display_name AS inviter_name, c.name AS subject_name, i.created_at
                FROM invitation i
                JOIN care_subject c ON c.subject_id = i.subject_id
                JOIN users o ON o.user_id = c.owner_user_id
                WHERE i.invitee_user_id = :u AND i.status = 'PENDING'
                ORDER BY i.created_at DESC, i.invitation_id DESC""")
                .param("u", userId)
                .query((rs, n) -> new ReceivedInvitation(rs.getLong("invitation_id"), rs.getString("inviter_name"),
                        rs.getString("subject_name"), PERMISSIONS, RESTRICTED_ITEMS,
                        rs.getTimestamp("created_at").toInstant()))
                .list());
    }

    @Transactional
    public AcceptResponse accept(Long invitationId, Long userId) {
        Long subjectId = requireOwnPending(invitationId, userId);
        respond(invitationId, InvitationStatus.ACCEPTED);
        jdbc.sql("INSERT INTO co_manager (subject_id, user_id, invitation_id, activated_at) VALUES (:s, :u, :i, :now)")
                .param("s", subjectId).param("u", userId).param("i", invitationId)
                .param("now", Timestamp.from(Instant.now())).update();
        return new AcceptResponse(subjectId, SubjectRole.CO_MANAGER);
    }

    @Transactional
    public void reject(Long invitationId, Long userId) {
        requireOwnPending(invitationId, userId);
        respond(invitationId, InvitationStatus.REJECTED);
    }

    /** 본인에게 온 초대가 아니면 INVITE_FORBIDDEN, 있어도 PENDING이 아니면 INVITE_NOT_PENDING */
    private Long requireOwnPending(Long invitationId, Long userId) {
        record Row(Long subjectId, Long inviteeId, String status) { }
        Row row = jdbc.sql("SELECT subject_id, invitee_user_id, status FROM invitation WHERE invitation_id = :i")
                .param("i", invitationId)
                .query((rs, n) -> new Row(rs.getLong("subject_id"), rs.getLong("invitee_user_id"), rs.getString("status")))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.INVITE_FORBIDDEN));
        if (!row.inviteeId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVITE_FORBIDDEN);
        }
        if (!InvitationStatus.PENDING.name().equals(row.status())) {
            throw new BusinessException(ErrorCode.INVITE_NOT_PENDING);
        }
        return row.subjectId();
    }

    /** status = 'PENDING' 조건 UPDATE라 동시에 처리해도 하나만 성공한다 */
    private void respond(Long invitationId, InvitationStatus to) {
        int n = jdbc.sql("UPDATE invitation SET status = :to, responded_at = :now WHERE invitation_id = :i AND status = 'PENDING'")
                .param("to", to.name()).param("now", Timestamp.from(Instant.now())).param("i", invitationId).update();
        if (n == 0) {
            throw new BusinessException(ErrorCode.INVITE_NOT_PENDING);
        }
    }

    public MembersResponse getMembers(Long subjectId, Long userId) {
        SubjectRole role = roles.require(subjectId, userId, SubjectRole.OWNER, SubjectRole.CO_MANAGER);
        boolean isOwner = role == SubjectRole.OWNER;
        Owner owner = jdbc.sql("""
                SELECT u.user_id, u.display_name, u.relation, u.login_id
                FROM care_subject c JOIN users u ON u.user_id = c.owner_user_id WHERE c.subject_id = :s""")
                .param("s", subjectId)
                .query((rs, n) -> new Owner(rs.getLong("user_id"), rs.getString("display_name"),
                        relation(rs.getString("relation")), isOwner ? rs.getString("login_id") : null))
                .single();
        List<CoManager> cos = jdbc.sql("""
                SELECT u.user_id, u.display_name, u.relation, u.login_id, m.activated_at
                FROM co_manager m JOIN users u ON u.user_id = m.user_id
                WHERE m.subject_id = :s ORDER BY m.activated_at, u.user_id""")
                .param("s", subjectId)
                .query((rs, n) -> new CoManager(rs.getLong("user_id"), rs.getString("display_name"),
                        relation(rs.getString("relation")),
                        isOwner || userId.equals(rs.getLong("user_id")) ? rs.getString("login_id") : null,
                        rs.getTimestamp("activated_at").toInstant()))
                .list();
        return new MembersResponse(owner, cos);
    }

    private static Relation relation(String v) {
        return v == null ? null : Relation.valueOf(v);
    }

    @Transactional
    public RemoveResponse removeMember(Long subjectId, Long memberUserId, Long userId) {
        roles.require(subjectId, userId, SubjectRole.OWNER);
        boolean isOwner = jdbc.sql("SELECT EXISTS (SELECT 1 FROM care_subject WHERE subject_id = :s AND owner_user_id = :u)")
                .param("s", subjectId).param("u", memberUserId).query(Boolean.class).single();
        if (isOwner) {
            throw new BusinessException(ErrorCode.MEMBER_CANNOT_REMOVE_OWNER);
        }
        Long invitationId = jdbc.sql("DELETE FROM co_manager WHERE subject_id = :s AND user_id = :u RETURNING invitation_id")
                .param("s", subjectId).param("u", memberUserId).query(Long.class).optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        jdbc.sql("UPDATE invitation SET status = 'REVOKED' WHERE invitation_id = :i").param("i", invitationId).update();
        Timestamp now = Timestamp.from(Instant.now());
        int revoked = jdbc.sql("""
                UPDATE share SET revoked_at = :now
                WHERE subject_id = :s AND created_by = :u AND approved_at IS NOT NULL
                  AND revoked_at IS NULL AND expires_at > :now""")
                .param("now", now).param("s", subjectId).param("u", memberUserId).update();
        return new RemoveResponse(revoked);
    }
}
