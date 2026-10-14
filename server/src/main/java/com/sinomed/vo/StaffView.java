package com.sinomed.vo;

import com.sinomed.entity.StaffEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Schema(title = "员工信息")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class StaffView implements Serializable {
    @Schema(title = "员工id",description = "员工id",example = "1234567890")
    Long id;
    @NotNull
    @Schema(title = "账号",description = "账号id",example = "cuihaitao")
    String account;
    @Schema(title = "头像",description = "头像展示的链接",example = "https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png")
    String avatar;
    @Schema(title = "密码",description = "密码",example = "123")
    String password;
    @NotNull
    @Schema(title = "员工姓名",description = "员工姓名",example = "崔海涛",requiredMode = Schema.RequiredMode.REQUIRED)
    String name;
    @Schema(title = "手机号码",description = "手机号码",example = "13852299511",requiredMode = Schema.RequiredMode.REQUIRED)
    String phone;
    @Schema(title = "地址",description = "地址",example = "江苏泰州市海宁区")
    String address;
    @Schema(title = "年纪",description = "年纪",example = "30")
    Integer age;
    @Schema(title = "性别",description = "性别",example = "男")
    Integer gender;
    @Schema(title = "职位",description = "职位" ,example = "1")
    @Builder.Default
    Integer role=99;
    @Schema(title = "职务",description = "自由文本职务名（如 医师/针灸医师/前台），可空；排班页医师名册=在职且职务含「医师」",example = "针灸医师")
    String title;
    @Schema(title = "名医等级",description = "0 普通（缺省）/ 1-3 名医，仅影响列表排序与标识徽章",example = "2")
    Integer famousLevel;
    @Schema(title = "状态",description = "0 离职 / 1 在职 / 2 休假（只读回显）",example = "1")
    Integer status;
    @Schema(title = "生日",description = "生日",example = "1990-1-1")
    Date birthday;
    @Schema(title = "创建时间",description = "创建时间",example = "2023-1-1 12:00:00")
    Date createTime;
    @Schema(title = "更新时间",description = "最近修改时间",example = "2023-1-1 12:00:00")
    Date updateTime;

    public StaffEntity toStaffEntity(){
        StaffEntity staffEntity = StaffEntity.getInstanceWithDefault(account);
        staffEntity.setId(id);
        staffEntity.setName(name);
        staffEntity.setPhone(phone);
        staffEntity.setAge(age);
        staffEntity.setAddress(address);
        staffEntity.setBirthday(birthday);
        staffEntity.setRole(role);
        staffEntity.setTitle(title);
        staffEntity.setFamousLevel(validFamousLevel(famousLevel));
        staffEntity.setAvatar(avatar);
        return staffEntity;
    }

    public void updateStaffEntity(StaffEntity staffEntity){
        if (name != null && !name.isEmpty() && !name.equals(staffEntity.getName())){
            staffEntity.setName(name);
        }
        if (phone != null && !phone.isEmpty() && !phone.equals(staffEntity.getPhone())){
            staffEntity.setPhone(phone);
        }
        if (address != null && !address.isEmpty() && !address.equals(staffEntity.getAddress())){
            staffEntity.setAddress(address);
        }
        if (age != null && age > 0 && age != staffEntity.getAge()){
            staffEntity.setAge(age);
        }
        if (gender != null && gender >= 0 && gender <=1 && gender != staffEntity.getGender()){
            staffEntity.setGender(gender);
        }
        if (role != null && role >= 0  && role != staffEntity.getRole()){
            staffEntity.setRole(role);
        }
        if (title != null && !title.isEmpty() && !title.equals(staffEntity.getTitle())){
            staffEntity.setTitle(title);
        }
        if (famousLevel != null){
            staffEntity.setFamousLevel(validFamousLevel(famousLevel));
        }
        if (birthday != null  && !birthday.equals(staffEntity.getBirthday())){
            staffEntity.setBirthday(birthday);
        }
    }

    /** 名医等级校验：null/0 为普通，1-3 为名医，越界报 400 */
    public static Integer validFamousLevel(Integer famousLevel) {
        if (famousLevel == null || famousLevel == 0) {
            return 0;
        }
        if (famousLevel < 1 || famousLevel > 3) {
            throw new IllegalArgumentException("名医等级无效：" + famousLevel + "（应为 0-3）");
        }
        return famousLevel;
    }

    public static StaffView fromStaffEntity(StaffEntity staffEntity){
        return StaffView.builder()
                .id(staffEntity.getId())
                .account(staffEntity.getAccount())
                .address(staffEntity.getAddress())
                .avatar(staffEntity.getAvatar())
                .age(staffEntity.getAge())
                .gender(staffEntity.getGender())
                .name(staffEntity.getName())
                .phone(staffEntity.getPhone())
                .birthday(staffEntity.getBirthday())
                .createTime(staffEntity.getCreateTime())
                .updateTime(staffEntity.getUpdateTime())
                .role(staffEntity.getRole())
                .title(staffEntity.getTitle())
                .famousLevel(staffEntity.getFamousLevel())
                .status(staffEntity.getStatus())
                .build();
    }
}
