package com.farmlog.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class CustomerUpdateRequest {
    @Size(min = 1, max = 150) @Pattern(regexp = ".*\\S.*") private String name;
    @Size(min = 1, max = 40) private String customerType;
    @Size(max = 50) private String phone;
    private String memo;
    private boolean phonePresent;
    private boolean memoPresent;

    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String customerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public String phone() { return phone; }
    @JsonSetter("phone") public void setPhone(String phone) { this.phone = phone; this.phonePresent = true; }
    public String memo() { return memo; }
    @JsonSetter("memo") public void setMemo(String memo) { this.memo = memo; this.memoPresent = true; }
    @JsonIgnore public boolean hasPhone() { return phonePresent; }
    @JsonIgnore public boolean hasMemo() { return memoPresent; }
}
