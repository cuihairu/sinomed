package com.sinomed.service.impl;

import com.sinomed.entity.AppointmentEntity;
import com.sinomed.entity.CustomerEntity;
import com.sinomed.entity.QueueCallEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.AppointmentRepository;
import com.sinomed.repository.CustomerRepository;
import com.sinomed.repository.QueueCallRepository;
import com.sinomed.repository.StaffRepository;
import com.sinomed.service.AppointmentService;
import com.sinomed.vo.AppointmentView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final QueueCallRepository queueCallRepository;
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;

    /**
     * 根据预约的id查询预约
     *
     * @param id
     */
    @Override
    public Optional<AppointmentEntity> findById(Long id) {
        return appointmentRepository.findById(id);
    }

    /**
     * 保存预约：新建一律 status=0 待到店，落库前做「每时段限约 1 位」校验；创建/更新时间由审计维护
     */
    @Override
    public AppointmentEntity save(AppointmentView view) {
        ensureSlotFree(view.getStartTime(), view.getStaffId());
        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setCustomerId(view.getCustomerId());
        appointment.setItemId(view.getItemId());
        appointment.setStaffId(view.getStaffId());
        appointment.setStartTime(view.getStartTime());
        appointment.setDuration(view.getDuration());
        appointment.setRemark(view.getRemark());
        appointment.setStatus(0);
        return appointmentRepository.save(appointment);
    }

    /**
     * 每时段限约 1 位（原型 admin-booking.html 口径）：同医师同日同小时已有 status 0/1 预约则拒；
     * staffId 为空（Kiosk 自助约期，到店分配）归同一空组参与互斥。取消/过号后的时段自动释放。
     */
    @Override
    public void ensureSlotFree(Date startTime, Long staffId) {
        if (startTime == null) {
            return;
        }
        // 对齐排班网格的整点桶：floor 到小时
        Date hourStart = Date.from(startTime.toInstant().atZone(ZoneId.systemDefault())
                .withMinute(0).withSecond(0).withNano(0).toInstant());
        Date hourEnd = new Date(hourStart.getTime() + 60L * 60 * 1000);
        // [start, end) 半开区间：BETWEEN 含右端点，会把下一整点的预约误算进上一格
        long taken = staffId == null
                ? appointmentRepository.countByStaffIdIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
                        hourStart, hourEnd, List.of(0, 1))
                : appointmentRepository.countByStaffIdAndStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(
                        staffId, hourStart, hourEnd, List.of(0, 1));
        if (taken > 0) {
            throw new IllegalArgumentException("该时段已约满（每时段限约 1 位），请换医师或时段");
        }
    }

    /**
     * 根据预约的id删除预约
     *
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        appointmentRepository.deleteById(id);
    }

    /**
     * 管理端分页；status 为空查全部
     */
    @Override
    public Page<AppointmentEntity> findPage(Integer status, Pageable pageable) {
        if (status == null) {
            return appointmentRepository.findAll(pageable);
        }
        return appointmentRepository.findByStatus(status, pageable);
    }

    /**
     * 状态流转：只允许 0→1（到店接待）、0→8（过号）与 0→9（取消）；过号/取消为终态
     */
    @Override
    public AppointmentEntity updateStatus(Long id, Integer status) {
        AppointmentEntity appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在：" + id));
        Integer current = appointment.getStatus();
        boolean allowed = (current == null || current == 0) && (status == 1 || status == 8 || status == 9);
        if (!allowed) {
            throw new IllegalArgumentException("预约状态不允许从 " + current + " 流转到 " + status);
        }
        appointment.setStatus(status);
        AppointmentEntity saved = appointmentRepository.save(appointment);
        if (status == 1) {
            fireQueueCall(saved);
        }
        return saved;
    }

    /**
     * 到店自动叫号（docs/tablet.md「到号自动叫号」联动，2026-10-12 拍板）：接待（0→1）落一条
     * queue_calls——号码=当日接待序号补零两位、诊室=接诊医师名+「诊室」（未指派「待分配诊室」）、
     * 姓名脱敏（张*）、广播全部屏（screenId 空）；过号/取消不叫号，前台手动叫号链路保留并行。
     */
    private void fireQueueCall(AppointmentEntity appointment) {
        QueueCallEntity call = new QueueCallEntity();
        call.setNumber(String.format("%02d", arrivalSeq(appointment)));
        call.setRoom(roomOf(appointment));
        call.setPatientMasked(maskName(customerRepository.findById(appointment.getCustomerId())
                .map(CustomerEntity::getName).orElse("")));
        call.setScreenId(null);
        call.setStatus(1);
        call.setCalledAt(new Date());
        queueCallRepository.save(call);
    }

    /** 当日接待序号：今天（接待时刻起算的自然日）已接待且先于本单的预约数 + 1，当日唯一递增 */
    private int arrivalSeq(AppointmentEntity appointment) {
        Date todayStart = Date.from(java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        long before = appointmentRepository
                .countByStatusAndIdLessThanAndUpdateTimeGreaterThanEqual(1, appointment.getId(), todayStart);
        return (int) before + 1;
    }

    private String roomOf(AppointmentEntity appointment) {
        if (appointment.getStaffId() == null) {
            return "待分配诊室";
        }
        return staffRepository.findById(appointment.getStaffId())
                .map(StaffEntity::getName).map(name -> name + "诊室")
                .orElse("待分配诊室");
    }

    /** 姓名脱敏：张 → 张*、张三 → 张*；空名不落（patient_masked 可空） */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name.charAt(0) + "*";
    }
}
