package com.sinomed.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Date;

import static com.sinomed.security.Role.ADMIN;
import static com.sinomed.security.Role.MANAGER;
import static com.sinomed.security.Role.USER;

@Data
@Entity
@Table(name = "`staffs`",indexes={
        @Index(name = "idx_staff_name",columnList = "name"),
        @Index(name = "idx_staff_account",columnList = "account"),
        @Index(name = "idx_staff_phone",columnList = "phone"),
})
@EntityListeners(AuditingEntityListener.class)
public class StaffEntity implements UserDetails  {
    static int LEADER = 10;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name",nullable = false)
    private String name;

    /**
     * account login id
     * */
    @Column(name = "account",nullable = false,unique = true)
    private String account;

    @Column(name = "age")
    private Integer age;

    /**
     * https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png
     * */
    @Column(name="avatar")
    private String avatar;

    @Column(name = "phone",unique = true)
    private String phone;

    @Column(name = "password")
    private String password;

    @Column(name = "status",nullable = false)
    private Integer status; // 0,离职 1,在职 2,休假

    @Column(name ="gender")
    private Integer gender;//0 女 1 男

    @Column(name = "address")
    private String address;

    @Column(name="role")
    private Integer role; // 0,boss, 99.普通员工

    /**
     * 职务（自由文本，可空）：权限档 role 之外的职务语义与名册数据源，
     * 如 医师/针灸医师/前台；排班页「医师名册」= 在职且职务含「医师」
     */
    @Column(name = "title")
    private String title;

    @Column(name = "birthday")
    private Date birthday;

    @CreatedDate
    @Column(name = "create_time")
    private Date createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private Date updateTime;

    public boolean isManager(){
        return role < LEADER;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 0 boss=ADMIN，1-9 管理层=MANAGER，其余（>=10 普通员工）=USER；
        // 方法级 @PreAuthorize（如员工管理写接口）据此判定
        if (role != null && role == 0) {
            return ADMIN.getAuthorities();
        }
        if (isManager()) {
            return MANAGER.getAuthorities();
        }
        return USER.getAuthorities();
    }

    public String getAccess(){
        if (isManager()) {
            return "admin";
        }
        return "user";
    }

    @Override
    public String getUsername() {
        return account;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status > 0;
    }

    public void setDefaultValue(){
        setRole(99);
        setStatus(1);
        setAge(18);
        setBirthday(new Date());
        setGender(1);
        setAddress("江苏泰州市");
        setAvatar("https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png");
    }

    public static StaffEntity getInstanceWithDefault(String account){
        StaffEntity staffEntity = new StaffEntity();
        staffEntity.setAccount(account);
        staffEntity.setName(account);
        staffEntity.setDefaultValue();
        return staffEntity;
    }
}
