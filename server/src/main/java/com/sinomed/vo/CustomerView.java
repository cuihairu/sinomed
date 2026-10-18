package com.sinomed.vo;

import com.sinomed.entity.CustomerEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(title = "客人信息")
public class CustomerView implements Serializable {
    @Schema(title = "客人id",description = "客人id",example = "123456")
    Long id;
    @NotNull
    @Schema(title = "名字",description = "名字",example = "崔海涛",requiredMode = Schema.RequiredMode.REQUIRED)
    String name;
    @Schema(title = "手机号码",description = "手机号码",example = "13852299511")
    String phone;
    @Schema(title = "地址",description = "地址",example = "江苏泰州市海宁区")
    String address;
    @Schema(title = "年纪",description = "年纪",example = "30")
    Integer age;
    @Schema(title = "性别",description = "性别",example = "男")
    Integer gender;
    @Schema(title = "会员等级",description = "会员等级",example = "1")
    Integer level;
    @Schema(title = "生日",description = "生日",example = "1990-1-1")
    Date birthday;
    @Schema(title = "创建时间",description = "创建时间",example = "2023-1-1 12:00:00")
    Date createTime;
    @Schema(title = "更新时间",description = "更新时间",example = "2023-1-1 12:00:00")
    Date updateTime;
    @Schema(title = "第一次诊断时间",description = "第一次诊断时间",example = "2023-1-1")
    Date firstDate;
    @Schema(title = "美容敏感信息单独同意",description = "1 已同意 / 0 未同意（缺省）；美容业态 d，PIPL 口径",example = "0")
    Integer beautyConsent;
    @Schema(title = "同意时刻",description = "同意接口写入")
    Date beautyConsentTime;
    @Schema(title = "经办员工id",description = "同意接口写入")
    Long beautyConsentStaffId;

    public void updateCustomerEntity(CustomerEntity customerEntity){
        if (name != null && !name.isEmpty() && !name.equals(customerEntity.getName())){
            customerEntity.setName(name);
        }
        if (phone != null && !phone.isEmpty() && !phone.equals(customerEntity.getPhone())){
            customerEntity.setPhone(phone);
        }
        if (address != null && !address.isEmpty() && !address.equals(customerEntity.getAddress())){
            customerEntity.setAddress(address);
        }
        if (age != null && age > 0 && age != customerEntity.getAge()){
            customerEntity.setAge(age);
        }
        if (gender != null && gender >= 0 && gender <=1 && gender != customerEntity.getGender()){
            customerEntity.setGender(gender);
        }
        if (level != null && level >= 0  && level != customerEntity.getLevel()){
            customerEntity.setLevel(level);
        }
        if (birthday != null  && !birthday.equals(customerEntity.getBirthday())){
            customerEntity.setBirthday(birthday);
        }
    }
    public CustomerEntity toCustomerEntity(){
        CustomerEntity customerEntity = new CustomerEntity();
        customerEntity.setName(name);
        if (phone != null) {
            customerEntity.setPhone(phone);
        }
        if (age != null){
            customerEntity.setAge(age);
        }else{
            customerEntity.setAge(18);
        }
        if (address!= null){
            customerEntity.setAddress(address);
        }else{
            customerEntity.setAddress("地球村");
        }
        if (birthday != null){
            customerEntity.setBirthday(birthday);
        }
        if (gender == null || gender > 1 || gender < 0){
            customerEntity.setGender(1);
        }else{
            customerEntity.setGender(gender);
        }
        if (level== null){
            customerEntity.setLevel(0);
        }else{
            customerEntity.setLevel(level);
        }
        return customerEntity;
    }

    public static CustomerView FromCustomerEntity( CustomerEntity customerEntity){
        return CustomerView.builder()
                .id(customerEntity.getId())
                .name(customerEntity.getName())
                .phone(customerEntity.getPhone())
                .gender(customerEntity.getGender())
                .age(customerEntity.getAge())
                .level(customerEntity.getLevel())
                .address(customerEntity.getAddress())
                .birthday(customerEntity.getBirthday())
                .beautyConsent(customerEntity.getBeautyConsent())
                .beautyConsentTime(customerEntity.getBeautyConsentTime())
                .beautyConsentStaffId(customerEntity.getBeautyConsentStaffId())
                .createTime(customerEntity.getCreateTime())
                .updateTime(customerEntity.getUpdateTime())
                .build();
    }
}
