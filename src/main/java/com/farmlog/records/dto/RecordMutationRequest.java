package com.farmlog.records.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** PATCH의 누락과 명시 null을 모든 선택 필드에서 구분하는 공통 요청 모델. */
public class RecordMutationRequest {
    private final Set<String> present = new HashSet<>();
    private LocalDate workDate, applyDate, harvestDate, salesDate;
    private Long zoneId, cropId, varietyId, seasonId, workTypeId, customerId, version;
    private BigDecimal workerCount, workHours, amountValue, quantity, unitPrice, feeAmount, netAmountOverride;
    private Integer preharvestIntervalDays;
    private String chemicalName, targetPest, dilutionRatio, amountUnit, grade, unit, packageUnit;
    private String itemName, settlementStatus, memo, clientRequestId;

    private void mark(String name) { present.add(name); }
    @JsonIgnore public boolean has(String name) { return present.contains(name); }
    @JsonIgnore public Set<String> presentFields() { return Set.copyOf(present); }

    public LocalDate workDate() { return workDate; }
    @JsonSetter public void setWorkDate(LocalDate v) { workDate=v; mark("workDate"); }
    public LocalDate applyDate() { return applyDate; }
    @JsonSetter public void setApplyDate(LocalDate v) { applyDate=v; mark("applyDate"); }
    public LocalDate harvestDate() { return harvestDate; }
    @JsonSetter public void setHarvestDate(LocalDate v) { harvestDate=v; mark("harvestDate"); }
    public LocalDate salesDate() { return salesDate; }
    @JsonSetter public void setSalesDate(LocalDate v) { salesDate=v; mark("salesDate"); }
    public Long zoneId() { return zoneId; }
    @JsonSetter public void setZoneId(Long v) { zoneId=v; mark("zoneId"); }
    public Long cropId() { return cropId; }
    @JsonSetter public void setCropId(Long v) { cropId=v; mark("cropId"); }
    public Long varietyId() { return varietyId; }
    @JsonSetter public void setVarietyId(Long v) { varietyId=v; mark("varietyId"); }
    public Long seasonId() { return seasonId; }
    @JsonSetter public void setSeasonId(Long v) { seasonId=v; mark("seasonId"); }
    public Long workTypeId() { return workTypeId; }
    @JsonSetter public void setWorkTypeId(Long v) { workTypeId=v; mark("workTypeId"); }
    public Long customerId() { return customerId; }
    @JsonSetter public void setCustomerId(Long v) { customerId=v; mark("customerId"); }
    public BigDecimal workerCount() { return workerCount; }
    @JsonSetter public void setWorkerCount(BigDecimal v) { workerCount=v; mark("workerCount"); }
    public BigDecimal workHours() { return workHours; }
    @JsonSetter public void setWorkHours(BigDecimal v) { workHours=v; mark("workHours"); }
    public String chemicalName() { return chemicalName; }
    @JsonSetter public void setChemicalName(String v) { chemicalName=v; mark("chemicalName"); }
    public String targetPest() { return targetPest; }
    @JsonSetter public void setTargetPest(String v) { targetPest=v; mark("targetPest"); }
    public String dilutionRatio() { return dilutionRatio; }
    @JsonSetter public void setDilutionRatio(String v) { dilutionRatio=v; mark("dilutionRatio"); }
    public BigDecimal amountValue() { return amountValue; }
    @JsonSetter public void setAmountValue(BigDecimal v) { amountValue=v; mark("amountValue"); }
    public String amountUnit() { return amountUnit; }
    @JsonSetter public void setAmountUnit(String v) { amountUnit=v; mark("amountUnit"); }
    public Integer preharvestIntervalDays() { return preharvestIntervalDays; }
    @JsonSetter public void setPreharvestIntervalDays(Integer v) { preharvestIntervalDays=v; mark("preharvestIntervalDays"); }
    public String grade() { return grade; }
    @JsonSetter public void setGrade(String v) { grade=v; mark("grade"); }
    public BigDecimal quantity() { return quantity; }
    @JsonSetter public void setQuantity(BigDecimal v) { quantity=v; mark("quantity"); }
    public String unit() { return unit; }
    @JsonSetter public void setUnit(String v) { unit=v; mark("unit"); }
    public String packageUnit() { return packageUnit; }
    @JsonSetter public void setPackageUnit(String v) { packageUnit=v; mark("packageUnit"); }
    public String itemName() { return itemName; }
    @JsonSetter public void setItemName(String v) { itemName=v; mark("itemName"); }
    public BigDecimal unitPrice() { return unitPrice; }
    @JsonSetter public void setUnitPrice(BigDecimal v) { unitPrice=v; mark("unitPrice"); }
    public BigDecimal feeAmount() { return feeAmount; }
    @JsonSetter public void setFeeAmount(BigDecimal v) { feeAmount=v; mark("feeAmount"); }
    public BigDecimal netAmountOverride() { return netAmountOverride; }
    @JsonSetter public void setNetAmountOverride(BigDecimal v) { netAmountOverride=v; mark("netAmountOverride"); }
    public String settlementStatus() { return settlementStatus; }
    @JsonSetter public void setSettlementStatus(String v) { settlementStatus=v; mark("settlementStatus"); }
    public String memo() { return memo; }
    @JsonSetter public void setMemo(String v) { memo=v; mark("memo"); }
    public String clientRequestId() { return clientRequestId; }
    @JsonSetter public void setClientRequestId(String v) { clientRequestId=v; mark("clientRequestId"); }
    public Long version() { return version; }
    @JsonSetter public void setVersion(Long v) { version=v; mark("version"); }
}
