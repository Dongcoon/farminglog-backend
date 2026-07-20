package com.farmlog.user;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.user.dto.AccessContextResponse;
import com.farmlog.user.dto.UserPreferenceRequest;
import com.farmlog.user.dto.UserPreferenceResponse;
import com.farmlog.user.dto.UserProfileResponse;
import com.farmlog.user.entity.UserEntity;
import com.farmlog.user.entity.UserPreferenceEntity;
import com.farmlog.user.mapper.UserAccessMapper;
import com.farmlog.user.mapper.UserMapper;
import com.farmlog.user.mapper.UserPreferenceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class UserService {

    private static final String YES = "Y";
    private static final String NO = "N";
    /** plan/04_Open_Questions_Log.md: 기본 보기 크기는 'standard'부터 시작(0-1절 확정 사항). */
    private static final String DEFAULT_VIEW_SCALE = "standard";
    private static final Set<String> VIEW_SCALES = Set.of("standard", "large", "xlarge", "max");

    private final UserMapper userMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final UserAccessMapper userAccessMapper;

    @Autowired
    public UserService(UserMapper userMapper, UserPreferenceMapper userPreferenceMapper,
                       UserAccessMapper userAccessMapper) {
        this.userMapper = userMapper;
        this.userPreferenceMapper = userPreferenceMapper;
        this.userAccessMapper = userAccessMapper;
    }

    UserService(UserMapper userMapper, UserPreferenceMapper userPreferenceMapper) {
        this(userMapper, userPreferenceMapper, null);
    }

    public AccessContextResponse getAccessContext(Long userId) {
        getActiveUserOrThrow(userId);
        var organizations = userAccessMapper.findActiveOrganizations(userId);
        var systemRoles = organizations.stream()
                .filter(row -> "SYSTEM".equals(row.getOrgType()) && "SYSTEM_ADMIN".equals(row.getRole()))
                .map(row -> "SYSTEM_ADMIN")
                .distinct()
                .toList();
        return new AccessContextResponse(userId, systemRoles, organizations.stream()
                .map(row -> new AccessContextResponse.OrganizationAccess(
                        row.getId(), row.getName(), row.getOrgType(), row.getRole(), row.getStatus()))
                .toList());
    }

    public UserProfileResponse getMyProfile(Long userId) {
        UserEntity user = getActiveUserOrThrow(userId);
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }

    public UserPreferenceResponse getMyPreferences(Long userId) {
        getActiveUserOrThrow(userId);
        return userPreferenceMapper.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new UserPreferenceResponse(DEFAULT_VIEW_SCALE, false, false, null));
    }

    @Transactional
    public UserPreferenceResponse updateMyPreferences(Long userId, UserPreferenceRequest request) {
        getActiveUserOrThrow(userId);
        validatePreferences(request);
        LocalDateTime now = LocalDateTime.now();

        UserPreferenceEntity existing = userPreferenceMapper.findByUserId(userId).orElse(null);
        if (existing == null) {
            UserPreferenceEntity toCreate = UserPreferenceEntity.builder()
                    .userId(userId)
                    .viewScale(request.viewScale())
                    .outdoorModeYn(toYn(request.outdoorMode()))
                    .reduceMotionYn(toYn(request.reduceMotion()))
                    .createdAt(now)
                    .build();
            try {
                userPreferenceMapper.insert(toCreate);
                return toResponse(toCreate);
            } catch (DuplicateKeyException concurrentInsert) {
                existing = userPreferenceMapper.findByUserId(userId)
                        .orElseThrow(() -> concurrentInsert);
            }
        }

        existing.setViewScale(request.viewScale());
        existing.setOutdoorModeYn(toYn(request.outdoorMode()));
        existing.setReduceMotionYn(toYn(request.reduceMotion()));
        existing.setUpdatedAt(now);
        userPreferenceMapper.update(existing);
        return toResponse(existing);
    }

    private void validatePreferences(UserPreferenceRequest request) {
        if (request == null || request.viewScale() == null || !VIEW_SCALES.contains(request.viewScale())
                || request.outdoorMode() == null || request.reduceMotion() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "환경설정 값이 올바르지 않습니다.");
        }
    }

    private UserEntity getActiveUserOrThrow(Long userId) {
        return userMapper.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserPreferenceResponse toResponse(UserPreferenceEntity entity) {
        return new UserPreferenceResponse(
                entity.getViewScale(),
                YES.equals(entity.getOutdoorModeYn()),
                YES.equals(entity.getReduceMotionYn()),
                entity.getLastSelectedFarmId()
        );
    }

    private String toYn(boolean value) {
        return value ? YES : NO;
    }
}
