
package com.stocksight.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_users_username", columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.SUPPLIER;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(name = "product_category", length = 120)
    private String productCategory;

    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus = "APPROVED";

    public User() {}

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public String getCompanyName() { return companyName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getProductCategory() { return productCategory; }
    public String getApprovalStatus() { return approvalStatus; }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(Role role) { this.role = role; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setProductCategory(String productCategory) { this.productCategory = productCategory; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
}
