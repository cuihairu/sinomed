package com.sinomed.repository;

import com.sinomed.entity.CommissionRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface CommissionRecordRepository extends JpaRepository<CommissionRecordEntity,Long>{

    Page<CommissionRecordEntity> findByOrderId(Long orderId, Pageable pageable);

    Page<CommissionRecordEntity> findByStaffId(Long staffId, Pageable pageable);

    /** 冲销：退单时取该结算单生成的全部正向记录 */
    List<CommissionRecordEntity> findBySettlementId(Long settlementId);

    /** 按员工合计（提成报表基数）：员工/起止可空；start 含、end 不含 */
    @Query("select r.staffId, sum(r.amount) from CommissionRecordEntity r " +
            "where (:staffId is null or r.staffId = :staffId) " +
            "and (:start is null or r.createTime >= :start) " +
            "and (:end is null or r.createTime < :end) " +
            "group by r.staffId order by sum(r.amount) desc")
    List<Object[]> summarize(@Param("staffId") Long staffId,
                             @Param("start") Date start,
                             @Param("end") Date end);
}
