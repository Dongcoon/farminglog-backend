package com.farmlog.user.mapper;

import com.farmlog.user.entity.UserPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserPreferenceMapper {

    Optional<UserPreferenceEntity> findByUserId(@Param("userId") Long userId);

    void insert(UserPreferenceEntity entity);

    void update(UserPreferenceEntity entity);
}
