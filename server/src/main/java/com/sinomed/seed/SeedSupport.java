package com.sinomed.seed;

import com.sinomed.entity.ItemEntity;
import com.sinomed.entity.StaffEntity;
import com.sinomed.repository.ItemRepository;
import com.sinomed.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * 演示种子共享工具（LAY-002 拆分）：各领域种子服务共用的「演示时间线回写」
 * （@CreatedDate 会覆盖显式时间，落库后 UPDATE 回写到过去）、相对时刻构造、
 * 按账号/名称查 id。失败只记日志，不阻断其余种子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedSupport {

    private final JdbcTemplate jdbcTemplate;
    private final StaffRepository staffRepository;
    private final ItemRepository itemRepository;

    /** 演示时间线回写通用化：把某行 create_time/update_time 改写到过去；失败只记日志 */
    public void backdateRow(String table, Long id, Date createTime) {
        try {
            jdbcTemplate.update("UPDATE " + table + " SET create_time = ?, update_time = ? WHERE id = ?",
                    new Timestamp(createTime.getTime()), new Timestamp(createTime.getTime()), id);
        } catch (Exception e) {
            log.warn("种子·时间回写失败（表 " + table + "，仅影响演示时间线，不影响功能）", e);
        }
    }

    /** 诊疗记录回写：创建时间回写到过去的接诊日，复诊历史/按日查询才有真实时间线 */
    public void backdateTreat(Long treatId, Date createTime) {
        backdateRow("treats", treatId, createTime);
    }

    public Long staffByAccount(String account) {
        return staffRepository.findAll().stream()
                .filter(s -> account.equals(s.getAccount()))
                .map(StaffEntity::getId)
                .findFirst().orElse(null);
    }

    public Long itemByName(String name) {
        return itemRepository.findAll().stream()
                .filter(i -> name.equals(i.getName()))
                .map(ItemEntity::getId)
                .findFirst().orElse(null);
    }

    /** 相对当天的某时刻（dayOffset 天后 hour:minute） */
    public Date at(int dayOffset, int hour, int minute) {
        return Date.from(LocalDate.now().plusDays(dayOffset).atTime(hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    /** n 天前 */
    public Date daysAgo(int n) {
        return day(LocalDate.now().minusDays((long) n));
    }

    /** n 天前的上午 10:30——诊疗落在营业时间；分页 endTime 为闭区间，00:00 整点会误入前一日的边界 */
    public Date daysAgoAt(int n) {
        return Date.from(LocalDate.now().minusDays((long) n).atTime(10, 30)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    /** n 天后 */
    public Date daysAhead(int n) {
        return day(LocalDate.now().plusDays((long) n));
    }

    private Date day(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
