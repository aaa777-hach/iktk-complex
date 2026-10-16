package ru.iktk.auth.dto;

import java.util.ArrayList;
import java.util.List;

public class UserInfoResponse {
    private String userId;
    private String username;
    private String displayName;
    private String email;
    private List<String> roles = new ArrayList<>();
    private String enterpriseCode;
    private String departmentCode;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public String getEnterpriseCode() { return enterpriseCode; }
    public void setEnterpriseCode(String enterpriseCode) { this.enterpriseCode = enterpriseCode; }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
}