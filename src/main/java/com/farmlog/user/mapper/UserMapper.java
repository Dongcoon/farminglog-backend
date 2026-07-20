package com.farmlog.user.mapper;

import com.farmlog.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface UserMapper {

    void insert(UserEntity user);

    Optional<UserEntity> findByEmail(@Param("email") String email);

    Optional<UserEntity> findById(@Param("id") Long id);

    void updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    void updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash, @Param("updatedAt") LocalDateTime updatedAt);
}
