package com.sinomed.service.impl;

import com.sinomed.entity.*;
import com.sinomed.repository.*;
import com.sinomed.service.ReviewService;
import com.sinomed.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewServiceImpl implements ReviewService {
    private final ReviewStaffRepository reviewStaffRepository;
    private final StaffRepository staffRepository;
    private final ReviewCustomerRepository reviewCustomerRepository;
    private final CustomerRepository customerRepository;
    private final ReviewRepository reviewRepository;
    @Override
    public ReviewStaffEntity saveStaffView(ReviewStaffEntity reviewStaffEntity) {
        if (reviewStaffEntity == null){
            throw new IllegalArgumentException("无效的回馈参数");
        }
        Date day = reviewStaffEntity.getDay();
        if (day == null){
            throw new IllegalArgumentException("无效的回馈参数");
        }
        String name = reviewStaffEntity.getName();
        if (reviewStaffEntity.getStaffId() == null && name != null){
            StaffEntity staffEntity = new StaffEntity();
            staffEntity.setName(name);
            Optional<StaffEntity> one = staffRepository.findOne(Example.of(staffEntity));
            one.ifPresent(entity -> {
                reviewStaffEntity.setStaffId(entity.getId());
                reviewStaffEntity.setCreateTime(entity.getCreateTime());
                reviewStaffEntity.setUpdateTime(entity.getUpdateTime());
            });
        }
        Date zeroTime = DateUtil.getZeroTime(day);
        reviewStaffEntity.setDay(zeroTime);
        return reviewStaffRepository.save(reviewStaffEntity);
    }

    @Override
    public ReviewCustomerEntity saveCustomerView(ReviewCustomerEntity reviewCustomerEntity) {
        if (reviewCustomerEntity == null){
            throw new IllegalArgumentException("无效的回馈参数");
        }
        Date day = reviewCustomerEntity.getDay();
        if (day == null){
            throw new IllegalArgumentException("无效的回馈参数");
        }
        String name = reviewCustomerEntity.getName();
        if (name != null && reviewCustomerEntity.getCustomerId() == null){
            CustomerEntity customerEntity = new CustomerEntity();
            customerEntity.setName(name);
            Optional<CustomerEntity> one = customerRepository.findOne(Example.of(customerEntity));
            one.ifPresent(entity -> {
                reviewCustomerEntity.setCustomerId(entity.getId());
                reviewCustomerEntity.setCreateTime(entity.getCreateTime());
                reviewCustomerEntity.setUpdateTime(entity.getUpdateTime());
            });
        }
        Date zeroTime = DateUtil.getZeroTime(day);
        reviewCustomerEntity.setDay(zeroTime);
        return reviewCustomerRepository.save(reviewCustomerEntity);
    }

    @Override
    public ReviewEntity saveView(ReviewEntity reviewEntity) {
        Date day = reviewEntity.getDay();
        if (day == null){
            day = new Date();
        }
        Date zeroTime = DateUtil.getZeroTime(day);
        ReviewEntity searchEntity = new ReviewEntity();
        searchEntity.setDay(zeroTime);
        Optional<ReviewEntity> one = reviewRepository.findOne(Example.of(searchEntity));
        if (one.isPresent()){
            ReviewEntity saveEntity = one.get();
            saveEntity.setGood(reviewEntity.getGood());
            saveEntity.setImprovement(reviewEntity.getImprovement());
            return reviewRepository.save(saveEntity);
        }
        reviewEntity.setDay(zeroTime);
        return reviewRepository.save(reviewEntity);
    }

    @Override
    public List<ReviewStaffEntity> findStaffViewByDate(Date date) {
        Date zeroTime = DateUtil.getZeroTime(date);
        ReviewStaffEntity reviewStaffEntity = new ReviewStaffEntity();
        reviewStaffEntity.setDay(zeroTime);
        return reviewStaffRepository.findAll(Example.of(reviewStaffEntity));
    }

    @Override
    public Optional<ReviewEntity> findViewByDate(Date date){
        Date zeroTime = DateUtil.getZeroTime(date);
        ReviewEntity reviewEntity = new ReviewEntity();
        reviewEntity.setDay(zeroTime);
        return reviewRepository.findOne(Example.of(reviewEntity));
    }
    @Override
    public List<ReviewCustomerEntity> findCustomerViewByDate(Date date) {
        Date zeroTime = DateUtil.getZeroTime(date);
        ReviewCustomerEntity reviewCustomerEntity = new ReviewCustomerEntity();
        reviewCustomerEntity.setDay(zeroTime);
        return reviewCustomerRepository.findAll(Example.of(reviewCustomerEntity));
    }

    @Override
    public List<ReviewCustomerEntity> findCustomerViewsByCustomer(Long customerId) {
        return reviewCustomerRepository.findByCustomerIdOrderByDayDesc(customerId);
    }

    @Override
    public boolean deleteStaffViewById(Long id) {
        reviewStaffRepository.deleteById(id);
        return true;
    }

    @Override
    public boolean deleteCustomerViewById(Long id) {
        reviewCustomerRepository.deleteById(id);
        return true;
    }

    @Override
    public Optional<ReviewEntity> findViewById(Long id) {
        return reviewRepository.findById(id);
    }

    @Override
    @Transactional
    public void deleteViewById(Long id) {
        reviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("复盘总结不存在：" + id));
        reviewRepository.deleteById(id);
    }
}
