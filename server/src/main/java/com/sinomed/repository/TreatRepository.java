package com.sinomed.repository;

import com.sinomed.entity.TreatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TreatRepository extends JpaRepository<TreatEntity,Long>, JpaSpecificationExecutor<TreatEntity> {

    /** 按顾客聚合就诊次数（排班页「首诊/复诊」角标等批量场景；无诊疗记录的顾客不出现在结果里） */
    interface VisitCount {
        Long getCustomerId();
        Long getVisits();
    }

    @Query("select t.customerId as customerId, count(t.id) as visits "
            + "from TreatEntity t where t.customerId in :ids group by t.customerId")
    List<VisitCount> countVisitsByCustomerIds(@Param("ids") Collection<Long> ids);
}
