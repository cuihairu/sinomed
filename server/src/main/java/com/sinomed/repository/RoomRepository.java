package com.sinomed.repository;

import com.sinomed.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<RoomEntity,Long>{

    /** 预约表单工位下拉：在用工位按 id 升序 */
    List<RoomEntity> findByEnabledOrderByIdAsc(Integer enabled);

    /** 按名称取工位（name 唯一） */
    java.util.Optional<RoomEntity> findByName(String name);
}
