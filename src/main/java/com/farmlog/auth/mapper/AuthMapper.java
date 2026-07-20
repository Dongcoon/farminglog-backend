package com.farmlog.auth.mapper;

import com.farmlog.auth.entity.OrganizationEntity;
import com.farmlog.auth.entity.OrganizationMemberEntity;
import com.farmlog.auth.entity.PasswordResetTokenEntity;
import com.farmlog.auth.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * auth 모듈이 소유하는 보조 테이블(개인 조직 자동 생성용 organization/organization_member,
 * refresh_token, password_reset_token) 접근 매퍼. {@code users} 테이블 자체는
 * {@link com.farmlog.user.mapper.UserMapper}가 단일 소유한다(중복 SQL 방지).
 */
@Mapper
public interface AuthMapper {

    void insertOrganization(OrganizationEntity organization);

    void insertOrganizationMember(OrganizationMemberEntity member);

    /**
     * 사용자가 ACTIVE 상태로 소속된 조직 멤버십을 조회한다. Phase 1 시점에는 가입 시 자동 생성되는
     * 개인(PERSONAL) 조직 하나뿐이라 사실상 유일한 행이 반환된다. 이후 다른 조직 멤버십이 추가되더라도
     * 농장을 임의의 기관 조직 아래 생성하지 않도록 활성 개인 조직만 조회한다(farm 모듈의
     * {@code POST /farms}가 organization_id를 서버 측에서 유추할 때 사용).
     */
    Optional<OrganizationMemberEntity> findActiveOrganizationMembershipByUserId(@Param("userId") Long userId);

    Optional<OrganizationMemberEntity> findActiveOrganizationMembershipForUpdate(
            @Param("organizationId") Long organizationId, @Param("userId") Long userId);

    void insertRefreshToken(RefreshTokenEntity token);

    Optional<RefreshTokenEntity> findRefreshTokenByHash(@Param("tokenHash") String tokenHash);

    void revokeRefreshToken(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt, @Param("replacedByTokenId") Long replacedByTokenId);

    void revokeAllRefreshTokensForUser(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);

    void insertPasswordResetToken(PasswordResetTokenEntity token);

    Optional<PasswordResetTokenEntity> findPasswordResetTokenByHash(@Param("tokenHash") String tokenHash);

    void markPasswordResetTokenUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
}
