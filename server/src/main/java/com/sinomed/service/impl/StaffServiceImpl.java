package com.sinomed.service.impl;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.SignEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.entity.TreatEntity;
import com.sinomed.repository.SignRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.StaffService;
import com.sinomed.util.DateUtil;
import com.sinomed.vo.TimesheetView;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;
    private final SignRepository signRepository;
    private static String defaultPassword ="$2a$10$7eWlG/5QWqWJ6ZXQsW8YzumIagZawwLiREv6lK8yIK9xvXCO1WDa2";// 123
    private static String defaultAdmin = "admin";

    private final PasswordEncoder passwordEncoder;
    /**
     * 根据员工的id查询员工
     *
     * @param id
     */
    @Override
    public Optional<StaffEntity> findById(Long id) {
        return staffRepository.findById(id);
    }

    @Override
    public Page<StaffEntity> findAllByPage(String name, Integer age, String phone, String startTime, String endTime, Pageable pageable) {
        if ((name == null || name.isEmpty()) && (age == null || age == 0) &&(phone==null || phone.isEmpty()) && (startTime==null || startTime.isEmpty()) &&(endTime==null || endTime.isEmpty()) ){
            return staffRepository.findAll(pageable);
        }
        Specification<StaffEntity> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (name != null && !name.isEmpty()){
                // name 按包含不区分大小写（全仓口径同 ItemServiceImpl；web 搜索框与 treat/fuzzy 模糊契约均按包含语义使用）
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name").as(String.class)),
                        "%" + name.trim().toLowerCase() + "%"));
            }
            if (age != null && age > 0) {
                predicates.add(criteriaBuilder.equal(root.get("age").as(Integer.class),age));
            }
            if (phone != null && !phone.isEmpty()){
                predicates.add(criteriaBuilder.equal(root.get("phone").as(String.class),phone));
            }
            // createTime 落库为毫秒整数，需按 Date 比较；字符串词法比较在 SQLite 永不命中
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            Predicate[] array = new Predicate[predicates.size()];
            return criteriaBuilder.and(predicates.toArray(array));
        };
        return staffRepository.findAll(specification,pageable);
    }

    @Override
    public Page<SignEntity> findSignAllByPage(Long staffId, String startTime, String endTime, Pageable pageable) {
        Specification<SignEntity> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (staffId != null && staffId >= 0){
                predicates.add(criteriaBuilder.equal(root.get("staffId").as(Long.class),staffId));
            }
            // createTime 落库为毫秒整数，需按 Date 比较；字符串词法比较在 SQLite 永不命中
            Date start = DateUtil.parseParam(startTime);
            if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.<Date>get("createTime"), start));
            }
            Date end = DateUtil.parseParam(endTime);
            if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Date>get("createTime"), end));
            }
            Predicate[] array = new Predicate[predicates.size()];
            return criteriaBuilder.and(predicates.toArray(array));
        };
        return signRepository.findAll(specification,pageable);
    }

    @Override
    public List<StaffEntity> findAll(StaffEntity staffEntity, Sort sort) {
        if (staffEntity != null){
            return staffRepository.findAll(Example.of(staffEntity),sort);
        }else{
            return staffRepository.findAll(sort);
        }
    }

    /**
     * @param staffId
     * @return
     */
    @Override
    public List<SignEntity> getTodayTimesheetList(Long staffId) {
        PageRequest pageRequest = PageRequest.ofSize(Integer.MAX_VALUE);
        Date now = new Date();
        String start = DateUtil.getZero(now);
        String end = DateUtil.getZero(DateUtil.getDateAfter(now,1));
        Page<SignEntity> signAllByPage = findSignAllByPage(staffId, start, end, pageRequest);
        return signAllByPage.toList();
    }

    @Override
    public StaffEntity create(String account, String password) throws IllegalArgumentException {
        Optional<StaffEntity> byAccount = findByAccount(account);
        if (byAccount.isPresent()){
            throw new IllegalArgumentException("用户名已经存在，请选择其他用户名");
        }
        StaffEntity staffEntity = StaffEntity.getInstanceWithDefault(account);
        staffEntity.setPassword(passwordEncoder.encode(password));
        return save(staffEntity);
    }

    @Override
    public StaffEntity updatePassword(StaffEntity staffEntity, String password) {
        String encode = passwordEncoder.encode(password);
        if (encode.equals(staffEntity.getPassword())){
            return staffEntity;
        }
        staffEntity.setPassword(encode);
        return save(staffEntity);
    }

    /**
     * 保存员工
     *
     * @param staffEntity
     */
    @Override
    public StaffEntity save(StaffEntity staffEntity) {
        Long id = staffEntity.getId();
        if (id!= null){
            Optional<StaffEntity> byId = staffRepository.findById(id);
            if (byId.isPresent()){
                staffEntity.setCreateTime(byId.get().getCreateTime());
                staffEntity.setUpdateTime(byId.get().getUpdateTime());
            }
        }
        return staffRepository.save(staffEntity);
    }

    /**
     * 根据员工的姓名查询员工
     *
     * @param name
     */
    @Override
    public Optional<StaffEntity> findByName(String name) {
        StaffEntity staffEntity = new StaffEntity();
        staffEntity.setName(name);
        return staffRepository.findOne(Example.of(staffEntity));
    }

    @Override
    @Cacheable("StaffEntityByAccount")
    public Optional<StaffEntity> findByAccount(String account) {
        StaffEntity staffEntity = new StaffEntity();
        staffEntity.setAccount(account);
        Optional<StaffEntity> one = staffRepository.findOne(Example.of(staffEntity));
        if (one.isEmpty() && account.equals(defaultAdmin)){
            staffEntity.setDefaultValue();
            staffEntity.setAccount(defaultAdmin);
            staffEntity.setName(defaultAdmin);
            staffEntity.setPassword(defaultPassword);
            staffEntity.setRole(0);
            save(staffEntity);
            return staffRepository.findOne(Example.of(staffEntity));
        }
        return one;
    }

    /**
     * 根据员工的id删除员工
     *
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        staffRepository.deleteById(id);
    }

    /**
     * 根据员工的手机号码查询员工
     *
     * @param phone
     */
    @Override
    public Optional<StaffEntity> findByPhone(String phone) {
        StaffEntity staffEntity = new StaffEntity();
        staffEntity.setPhone(phone);
        return staffRepository.findOne(Example.of(staffEntity));
    }

    /**
     * 员工签到
     *
     * @param staffId
     * @param type
     */
    @Override
    public SignEntity sign(Long staffId, Integer type) {
        SignEntity signEntity = new SignEntity();
        signEntity.setStaffId(staffId);
        signEntity.setType(type);
        return signRepository.save(signEntity);
    }
}
