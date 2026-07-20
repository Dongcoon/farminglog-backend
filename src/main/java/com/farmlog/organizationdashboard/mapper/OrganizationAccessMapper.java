package com.farmlog.organizationdashboard.mapper;

import com.farmlog.organizationdashboard.entity.OrganizationAccessRow;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationAccessMapper {
  Optional<OrganizationAccessRow> findActiveOrgAdmin(
      @Param("userId") Long userId, @Param("organizationId") Long organizationId);
}
