package com.sinomed.service.impl;

import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.CustomerHistoryEntity;
import com.sinomed.repository.CustomerHistoryRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.service.CustomerService;
import com.sinomed.util.DateUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerHistoryRepository historyRepository;

    @Override
    public Page<CustomerEntity> findAllByPage(String name,Integer age,String phone, String startTime,String endTime, Pageable pageable){
        if ((name == null || name.isEmpty()) && (age == null || age == 0) &&(phone==null || phone.isEmpty()) && (startTime==null || startTime.isEmpty()) &&(endTime==null || endTime.isEmpty()) ){
            return customerRepository.findAll(pageable);
        }
        Specification<CustomerEntity> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new LinkedList<>();
            if (name != null && !name.isEmpty()){
                predicates.add(criteriaBuilder.equal(root.get("name").as(String.class),name));
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
        return customerRepository.findAll(specification,pageable);
    }
    /**
     * @param id
     * @return
     */
    @Override
    @Cacheable("customerEntity")
    public Optional<CustomerEntity> findById(Long id) {
        return customerRepository.findById(id);
    }

    @Override
    public CustomerEntity save(CustomerEntity customerEntity){
        Long id = customerEntity.getId();
        if (id != null){
            Optional<CustomerEntity> byId = customerRepository.findById(id);
            if (!byId.isEmpty()){
                customerEntity.setCreateTime(byId.get().getCreateTime());
                customerEntity.setUpdateTime(byId.get().getUpdateTime());
            }
        }
        return customerRepository.save(customerEntity);
    }

    @Override
    public List<CustomerEntity> findByName(String name){
        CustomerEntity userEntity = new CustomerEntity();
        userEntity.setName(name);
        return customerRepository.findAll(Example.of(userEntity));
    }

    @Override
    public Optional<CustomerEntity> findByPhone(String phone){
        CustomerEntity customerEntity = new CustomerEntity();
        customerEntity.setPhone(phone);
        return customerRepository.findOne(Example.of(customerEntity));
    }

    /**
     * 根据用户的id删除用户
     *
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        customerRepository.deleteById(id);
    }

    @Override
    public List<CustomerHistoryEntity> listHistories(Long customerId) {
        customerRepository.findById(customerId).orElseThrow(() ->
                new IllegalArgumentException("顾客不存在，无法查询病史"));
        return historyRepository.findByCustomerIdOrderByIdDesc(customerId);
    }

    @Override
    public CustomerHistoryEntity addHistory(Long customerId, Integer type, String content) {
        customerRepository.findById(customerId).orElseThrow(() ->
                new IllegalArgumentException("顾客不存在，无法记录病史"));
        if (type == null || (type != 0 && type != 1 && type != 2)) {
            throw new IllegalArgumentException("病史类型无效：0 过敏 / 1 既往 / 2 护理方案");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("病史内容不能为空");
        }
        if (text.length() > 200) {
            throw new IllegalArgumentException("病史内容过长（≤200 字）");
        }
        CustomerHistoryEntity history = new CustomerHistoryEntity();
        history.setCustomerId(customerId);
        history.setType(type);
        history.setContent(text);
        CustomerHistoryEntity saved = historyRepository.save(history);
        log.info("病史记录：customer={} type={}（{}）内容 {} 字", customerId, type,
                type == 0 ? "过敏" : type == 1 ? "既往" : "护理方案", text.length());
        return saved;
    }

    @Override
    public void deleteHistory(Long historyId) {
        CustomerHistoryEntity history = historyRepository.findById(historyId).orElseThrow(() ->
                new IllegalArgumentException("病史记录不存在：" + historyId));
        historyRepository.delete(history);
    }
}
