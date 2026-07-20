package com.farmlog.masterdata.mapper;

import com.farmlog.masterdata.entity.CatalogEntity;
import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.entity.FarmCatalogRow;
import com.farmlog.masterdata.entity.LocalMasterDataRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MasterDataMapper {
    List<FarmCatalogRow> findFarmCrops(@Param("farmId") Long farmId, @Param("includeInactive") boolean includeInactive);
    Optional<FarmCatalogRow> findFarmCrop(@Param("farmId") Long farmId, @Param("cropId") Long cropId);
    Optional<CatalogEntity> findCropByName(@Param("name") String name);
    void insertCropIfAbsent(@Param("name") String name);
    int insertFarmCropIfAbsent(@Param("farmId") Long farmId, @Param("cropId") Long cropId,
                                @Param("displayOrder") int displayOrder, @Param("userId") Long userId,
                                @Param("now") LocalDateTime now);
    void updateFarmCrop(@Param("farmId") Long farmId, @Param("cropId") Long cropId,
                        @Param("displayOrder") int displayOrder, @Param("activeYn") String activeYn,
                        @Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<FarmCatalogRow> findFarmVarieties(@Param("farmId") Long farmId, @Param("cropId") Long cropId,
                                           @Param("includeInactive") boolean includeInactive);
    Optional<FarmCatalogRow> findFarmVariety(@Param("farmId") Long farmId, @Param("cropId") Long cropId,
                                             @Param("varietyId") Long varietyId);
    Optional<CatalogEntity> findVarietyByName(@Param("cropId") Long cropId, @Param("name") String name);
    void insertVarietyIfAbsent(@Param("cropId") Long cropId, @Param("name") String name);
    int insertFarmVarietyIfAbsent(@Param("farmId") Long farmId, @Param("cropId") Long cropId,
                                   @Param("varietyId") Long varietyId, @Param("displayOrder") int displayOrder,
                                   @Param("userId") Long userId, @Param("now") LocalDateTime now);
    void updateFarmVariety(@Param("farmId") Long farmId, @Param("varietyId") Long varietyId,
                           @Param("cropId") Long cropId, @Param("displayOrder") int displayOrder,
                           @Param("activeYn") String activeYn, @Param("userId") Long userId,
                           @Param("now") LocalDateTime now);

    List<CropSeasonEntity> findCropSeasons(@Param("farmId") Long farmId, @Param("includeCompleted") boolean includeCompleted);
    Optional<CropSeasonEntity> findCropSeason(@Param("farmId") Long farmId, @Param("seasonId") Long seasonId);
    void insertCropSeason(@Param("season") CropSeasonEntity season, @Param("userId") Long userId,
                          @Param("now") LocalDateTime now);
    void updateCropSeason(@Param("season") CropSeasonEntity season, @Param("userId") Long userId,
                          @Param("now") LocalDateTime now);
    int migrateActiveSeasonsForCropVariety(@Param("farmId") Long farmId,
                                            @Param("oldCropId") Long oldCropId,
                                            @Param("oldVarietyId") Long oldVarietyId,
                                            @Param("newCropId") Long newCropId,
                                            @Param("newVarietyId") Long newVarietyId,
                                            @Param("userId") Long userId,
                                            @Param("now") LocalDateTime now);
    int migrateActiveSeasonsWithoutVariety(@Param("farmId") Long farmId,
                                           @Param("oldCropId") Long oldCropId,
                                           @Param("newCropId") Long newCropId,
                                           @Param("userId") Long userId,
                                           @Param("now") LocalDateTime now);
    int migrateActiveSeasonsForVariety(@Param("farmId") Long farmId,
                                       @Param("cropId") Long cropId,
                                       @Param("oldVarietyId") Long oldVarietyId,
                                       @Param("newVarietyId") Long newVarietyId,
                                       @Param("userId") Long userId,
                                       @Param("now") LocalDateTime now);
    void completeCropSeason(@Param("farmId") Long farmId, @Param("seasonId") Long seasonId,
                            @Param("endDate") LocalDate endDate, @Param("userId") Long userId,
                            @Param("now") LocalDateTime now);
    void softDeleteCropSeason(@Param("farmId") Long farmId, @Param("seasonId") Long seasonId,
                              @Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<LocalMasterDataRow> findWorkTypes(@Param("farmId") Long farmId, @Param("includeInactive") boolean includeInactive);
    Optional<LocalMasterDataRow> findWorkType(@Param("farmId") Long farmId, @Param("id") Long id);
    Optional<LocalMasterDataRow> findWorkTypeByName(@Param("farmId") Long farmId, @Param("name") String name);
    int insertWorkTypeIfAbsent(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
                                @Param("name") String name, @Param("displayOrder") int displayOrder,
                                @Param("userId") Long userId, @Param("now") LocalDateTime now);
    void updateWorkType(@Param("farmId") Long farmId, @Param("id") Long id, @Param("name") String name,
                        @Param("displayOrder") int displayOrder, @Param("activeYn") String activeYn,
                        @Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<LocalMasterDataRow> findCustomers(@Param("farmId") Long farmId, @Param("includeInactive") boolean includeInactive);
    Optional<LocalMasterDataRow> findCustomer(@Param("farmId") Long farmId, @Param("id") Long id);
    Optional<LocalMasterDataRow> findCustomerByName(@Param("farmId") Long farmId, @Param("name") String name);
    void insertCustomer(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
                        @Param("name") String name, @Param("type") String type, @Param("phone") String phone,
                        @Param("memo") String memo, @Param("userId") Long userId, @Param("now") LocalDateTime now);
    void updateCustomer(@Param("farmId") Long farmId, @Param("id") Long id, @Param("name") String name,
                        @Param("type") String type, @Param("phone") String phone, @Param("memo") String memo,
                        @Param("activeYn") String activeYn, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<LocalMasterDataRow> findMaterials(@Param("farmId") Long farmId, @Param("includeInactive") boolean includeInactive);
    Optional<LocalMasterDataRow> findMaterial(@Param("farmId") Long farmId, @Param("id") Long id);
    Optional<LocalMasterDataRow> findMaterialByName(@Param("farmId") Long farmId, @Param("name") String name);
    void insertMaterial(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
                        @Param("name") String name, @Param("type") String type, @Param("unit") String unit,
                        @Param("memo") String memo, @Param("userId") Long userId, @Param("now") LocalDateTime now);
    void updateMaterial(@Param("farmId") Long farmId, @Param("id") Long id, @Param("name") String name,
                        @Param("type") String type, @Param("unit") String unit, @Param("memo") String memo,
                        @Param("activeYn") String activeYn, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
