package com.batong.user.dto;

import com.batong.global.auth.SubjectRole;

/** AUTH-03, USER-01의 subjects[] */
public record SubjectSummary(Long subjectId, String name, SubjectRole role) {
}
