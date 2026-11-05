package com.batong.user.service;

import com.batong.global.auth.SubjectRole;
import com.batong.global.enums.Relation;
import com.batong.user.dto.SubjectSummary;
import com.batong.user.dto.UserDtos.HomeResponse;
import com.batong.user.dto.UserDtos.MeResponse;
import com.batong.user.dto.UserDtos.ProfileUpdateRequest;
import com.batong.user.dto.UserDtos.ProfileUpdateResponse;
import com.batong.user.dto.UserDtos.RecentActivity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 스텁: API 명세 예시를 돌려준다. 실제 구현은 #7(USER), #9(HOME) */
@Service
public class UserService {

    public MeResponse getMe(Long userId) {
        return new MeResponse(1L, "mom1234", "김영희", Relation.PARENT,
                List.of(new SubjectSummary(10L, "민지", SubjectRole.OWNER)), 1);
    }

    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        return new ProfileUpdateResponse(1L, "김영희", Relation.PARENT);
    }

    public void withdraw(Long userId) {
    }

    public HomeResponse getHome(Long subjectId, Long userId) {
        return new HomeResponse(SubjectRole.OWNER, "민지", false,
                List.of(new RecentActivity(501L, LocalDate.of(2026, 11, 3), "오후에 옆 건물 공사 소리가...", 3, 0)),
                0);
    }
}
