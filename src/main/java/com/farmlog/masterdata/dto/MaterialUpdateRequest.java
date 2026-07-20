package com.farmlog.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class MaterialUpdateRequest {
    @Size(min = 1, max = 150) @Pattern(regexp = ".*\\S.*") private String name;
    @Size(min = 1, max = 40) private String materialType;
    @Size(max = 20) private String unit;
    private String memo;
    private boolean unitPresent;
    private boolean memoPresent;

    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String materialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    public String unit() { return unit; }
    @JsonSetter("unit") public void setUnit(String unit) { this.unit = unit; this.unitPresent = true; }
    public String memo() { return memo; }
    @JsonSetter("memo") public void setMemo(String memo) { this.memo = memo; this.memoPresent = true; }
    @JsonIgnore public boolean hasUnit() { return unitPresent; }
    @JsonIgnore public boolean hasMemo() { return memoPresent; }
}
