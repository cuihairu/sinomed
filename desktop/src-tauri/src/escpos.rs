//! ESC/POS 直连打印（desktop.md D8）：免驱小票机场景。
//! 小票数据编码成 ESC/POS 字节流写串口（USB 转串口 / 板载串口），中文按 GBK 直发
//! （多数国产热敏机默认 GBK 字库）；打印对话框、驱动一概不经过。

use std::io::Write;
use std::sync::Mutex;
use std::time::Duration;

use serde_json::Value;
use tauri::State;

/// 控制指令：初始化 / 对齐 / 字号 / 加粗 / 走纸 / 切纸
const INIT: &[u8] = &[0x1b, 0x40];
const ALIGN_LEFT: &[u8] = &[0x1b, 0x61, 0x00];
const ALIGN_CENTER: &[u8] = &[0x1b, 0x61, 0x01];
const SIZE_NORMAL: &[u8] = &[0x1d, 0x21, 0x00];
const SIZE_DOUBLE: &[u8] = &[0x1d, 0x21, 0x11];
const BOLD_ON: &[u8] = &[0x1b, 0x45, 0x01];
const BOLD_OFF: &[u8] = &[0x1b, 0x45, 0x00];
const FEED: &[u8] = &[0x0a, 0x0a, 0x0a];
const CUT: &[u8] = &[0x1d, 0x56, 0x01];

/// 文本按打印机字模算显示宽（ASCII 1 列，中文等全角 2 列）
fn display_width(text: &str) -> usize {
    text.chars()
        .map(|c| if c.is_ascii() { 1 } else { 2 })
        .sum()
}

/// GBK 编码（打印机侧中文字库的事实标准）
fn gbk(text: &str) -> Vec<u8> {
    let (bytes, _, _) = encoding_rs::GBK.encode(text);
    bytes.into_owned()
}

/// 左右两段拼到一行：中缝用空格补齐到 columns 列；放不下就不补（打印机自动换行兜底）
fn join_row(left: &str, right: &str, columns: usize) -> String {
    let pad = columns
        .saturating_sub(display_width(left) + display_width(right))
        .max(1);
    format!("{left}{}{right}\n", " ".repeat(pad))
}

/// 小票数据 → ESC/POS 字节流（字段与 print_html 的 receipt 模板同源）：
/// 店名倍宽居中，单号 / 客人 / 时间明细行，卡项「名称 ×数量 …… 金额」，合计加粗，走纸切纸。
pub fn encode_receipt(data: &Value, columns: usize) -> Vec<u8> {
    let get = |key: &str| {
        data.get(key)
            .map(|v| match v {
                Value::String(s) => s.clone(),
                Value::Null => String::new(),
                other => other.to_string(),
            })
            .unwrap_or_default()
    };
    let divider = "-".repeat(columns);

    let mut out = Vec::new();
    out.extend_from_slice(INIT);
    // 店名：居中 + 倍宽倍高
    out.extend_from_slice(ALIGN_CENTER);
    out.extend_from_slice(SIZE_DOUBLE);
    out.extend_from_slice(&gbk(&get("clinicName")));
    out.push(0x0a);
    out.extend_from_slice(SIZE_NORMAL);
    out.extend_from_slice(ALIGN_LEFT);
    for key in ["orderNo", "customerName", "date"] {
        let value = get(key);
        if !value.is_empty() {
            let label = match key {
                "orderNo" => "单号 ",
                "customerName" => "客人 ",
                _ => "",
            };
            out.extend_from_slice(&gbk(&format!("{label}{value}\n")));
        }
    }
    out.extend_from_slice(gbk(&divider).as_slice());
    out.push(0x0a);
    if let Some(items) = data.get("items").and_then(|v| v.as_array()) {
        for item in items {
            let name = item
                .get("name")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let qty = item.get("qty").and_then(Value::as_str).unwrap_or("1");
            let amount = item
                .get("amount")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let left = if qty.is_empty() {
                name.to_string()
            } else {
                format!("{name} ×{qty}")
            };
            out.extend_from_slice(gbk(&join_row(&left, amount, columns)).as_slice());
        }
    }
    out.extend_from_slice(gbk(&divider).as_slice());
    out.push(0x0a);
    out.extend_from_slice(BOLD_ON);
    out.extend_from_slice(gbk(&join_row("合计", &get("total"), columns)).as_slice());
    out.extend_from_slice(BOLD_OFF);
    let staff = get("staff");
    if !staff.is_empty() {
        out.extend_from_slice(gbk(&join_row("收银", &staff, columns)).as_slice());
    }
    out.extend_from_slice(FEED);
    out.extend_from_slice(CUT);
    out
}

/// 小票机按需直写串口，无长连接——状态条只能报「上次打印结果」，故在此留痕
#[derive(Default)]
pub struct EscposState {
    last: Mutex<Option<LastPrint>>,
}

/// 最近一次 ESC/POS 打印：at_unix 秒（状态条算相对时间）、bytes 已写字节、error 失败原因
#[derive(Default, Clone)]
struct LastPrint {
    at_unix: u64,
    bytes: usize,
    error: Option<String>,
}

impl EscposState {
    /// 记录一次打印结果（成功记字节数、error = None；失败记原因、bytes 归零）
    pub(crate) fn record(&self, bytes: usize, error: Option<String>) {
        let at_unix = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        *self.last.lock().expect("打印状态锁") = Some(LastPrint { at_unix, bytes, error });
    }

    /// 只读快照（peripheral_status 用）：ok / bytes / error / 距上次打印秒数
    pub(crate) fn snapshot(&self) -> Option<(bool, usize, Option<String>, u64)> {
        let guard = self.last.lock().expect("打印状态锁");
        let last = guard.as_ref()?;
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let ok = last.error.is_none();
        Some((ok, last.bytes, last.error.clone(), now.saturating_sub(last.at_unix)))
    }
}

/// 打开串口直发字节流：5 秒超时（写完即走，不等待打印完成）；结果留痕供外设状态查询
#[tauri::command]
pub fn print_escpos(
    state: State<'_, EscposState>,
    port: String,
    data: Value,
    columns: Option<u16>,
    baud: Option<u32>,
) -> Result<usize, String> {
    let bytes = encode_receipt(&data, columns.unwrap_or(32) as usize);
    let result = (|| -> Result<usize, String> {
        let mut serial = serialport::new(&port, baud.unwrap_or(9600))
            .timeout(Duration::from_secs(5))
            .open()
            .map_err(|e| format!("打开小票机串口 {port} 失败：{e}"))?;
        serial
            .write_all(&bytes)
            .map_err(|e| format!("写串口失败：{e}"))?;
        serial
            .flush()
            .map_err(|e| format!("刷写串口失败：{e}"))?;
        Ok(bytes.len())
    })();
    match &result {
        Ok(size) => state.record(*size, None),
        Err(err) => state.record(0, Some(err.clone())),
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn sample() -> Value {
        json!({
            "clinicName": "示例医馆",
            "orderNo": "12",
            "customerName": "测试客人",
            "date": "2026-10-08 10:20",
            "items": [
                { "name": "艾灸调理", "qty": "1", "amount": "¥100" },
                { "name": "腹部推拿", "qty": "2", "amount": "¥360" }
            ],
            "total": "¥460",
            "staff": "前台"
        })
    }

    #[test]
    fn stream_carries_init_and_cut_commands() {
        let bytes = encode_receipt(&sample(), 32);
        assert!(bytes.starts_with(INIT));
        // 合计行加粗后必须恢复，切纸指令收尾
        let bold_on = bytes.windows(BOLD_ON.len()).any(|w| w == BOLD_ON);
        let bold_off = bytes.windows(BOLD_OFF.len()).any(|w| w == BOLD_OFF);
        assert!(bold_on && bold_off);
        assert!(bytes.ends_with(CUT));
    }

    #[test]
    fn chinese_text_encoded_as_gbk() {
        let bytes = encode_receipt(&sample(), 32);
        // 「合计」的 GBK 字节（ASCII 金额不参与）
        let heji = gbk("合计");
        assert!(
            bytes.windows(heji.len()).any(|w| w == heji.as_slice()),
            "GBK bytes of 合计 must appear"
        );
        // 店名倍宽居中段：SIZE_DOUBLE 紧跟其后出现店名 GBK
        let name = gbk("示例医馆");
        let pos = bytes
            .windows(name.len())
            .position(|w| w == name.as_slice())
            .expect("店名 GBK");
        let size_pos = bytes
            .windows(SIZE_DOUBLE.len())
            .position(|w| w == SIZE_DOUBLE)
            .expect("SIZE_DOUBLE");
        assert!(size_pos < pos);
    }

    #[test]
    fn rows_padded_to_columns_by_display_width() {
        // 全角按 2 列算：「艾灸调理 ×1」= 8+1+2+1 = 12 列，右侧「¥100」= 2+3 = 5 列，
        // 中缝补 32-17=15 空格；行内另有左侧自带的 1 个空格，合计 16
        let row = join_row("艾灸调理 ×1", "¥100", 32);
        assert_eq!(display_width("艾灸调理 ×1"), 12);
        assert_eq!(row.chars().filter(|c| *c == ' ').count(), 16);
        // 放不下时至少 1 个空格分隔
        let tight = join_row(&"很长的项目名称很长".repeat(4), "¥100", 32);
        assert!(tight.matches(' ').count() >= 1);
    }

    #[test]
    fn missing_fields_render_blank_not_panic() {
        let bytes = encode_receipt(&json!({}), 32);
        assert!(bytes.starts_with(INIT));
        assert!(bytes.ends_with(CUT));
    }

    #[test]
    fn last_print_snapshot_reports_success_then_failure() {
        // 状态条口径：从未打印 = None；成功 = ok + 字节数 + error 空；失败 = error + bytes 归零
        let state = EscposState::default();
        assert_eq!(state.snapshot(), None);

        state.record(245, None);
        let (ok, bytes, error, ago) = state.snapshot().expect("已打印过");
        assert!(ok);
        assert_eq!(bytes, 245);
        assert_eq!(error, None);
        assert!(ago <= 1, "刚打印过，距今应在 1 秒内");

        state.record(0, Some("打开小票机串口 /dev/null 失败".into()));
        let (ok, bytes, error, _) = state.snapshot().expect("已打印过");
        assert!(!ok);
        assert_eq!(bytes, 0);
        assert!(error.as_deref().unwrap_or_default().contains("失败"));
    }
}
