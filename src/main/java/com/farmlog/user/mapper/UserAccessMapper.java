package com.farmlog.user.mapper;

import com.farmlog.user.entity.OrganizationAccessRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccessMapper {
  List<OrganizationAccessRow> findActiveOrganizations(@Param("userId") Long userId);

  boolean existsActiveSystemAdmin(@Param("userId") Long userId);
}
