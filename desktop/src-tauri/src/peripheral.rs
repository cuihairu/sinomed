//! 外设状态查询（desktop.md D10）：接诊页外设状态条的真实数据源。
//! 壳侧只读汇总三类外设：扫码枪（串口读线程运行时状态）、80mm 小票机（ESC/POS 上次打印结果）、
//! A5 处方笺打印机（系统打印，`config.printer` 仅预选记录）。
//! 纯查询不改外设状态——故远程加载的管理端页可放行（D9 不放行的仍是 print_/scanner_/set_ 等改状态命令）。

use tauri::{AppHandle, State};

use crate::commands::load;
use crate::escpos::EscposState;
use crate::scanner::ScannerState;

/// 三类外设的只读状态汇总（接诊页外设状态条一次性取全）
#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PeripheralStatus {
    pub scanner: ScannerStatus,
    pub escpos: EscposStatus,
    pub printer: PrinterStatus,
}

/// 扫码枪：`port` 空 = 未配串口（走 HID 键盘仿真）；
/// `state` = listening 在读 / idle 未开 / error 打开或读取失败
#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScannerStatus {
    pub port: String,
    pub state: String,
    pub error: Option<String>,
}

/// 小票机：免驱按需直写串口、无长连接，故只报上次打印结果；
/// `status` = ready 从未打印 / ok 上次成功 / error 上次失败
#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EscposStatus {
    pub mode: String,
    pub status: String,
    pub bytes: Option<usize>,
    pub ago_sec: Option<u64>,
    pub error: Option<String>,
}

/// 处方笺打印机：走系统打印对话框，`name` 为 config.printer 预选记录（空 = 未预选）
#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PrinterStatus {
    pub name: String,
    pub mode: String,
}

/// 外设状态查询：扫码枪取读线程运行时、小票机取上次打印结果、打印机取配置预选名
#[tauri::command]
pub fn peripheral_status(
    app: AppHandle,
    scanner: State<'_, ScannerState>,
    escpos: State<'_, EscposState>,
) -> PeripheralStatus {
    let (port, listening, error) = scanner.snapshot();
    // 有错优先报错，其次看是否在收码，都没则是未开（未配端口也归此态）
    let (state, scanner_error) = if error.is_some() {
        ("error".to_string(), error)
    } else if listening {
        ("listening".to_string(), None)
    } else {
        ("idle".to_string(), None)
    };
    let escpos_last = escpos.snapshot();
    PeripheralStatus {
        scanner: ScannerStatus {
            port,
            state,
            error: scanner_error,
        },
        escpos: EscposStatus {
            mode: "ondemand".into(),
            status: match &escpos_last {
                Some((true, ..)) => "ok".into(),
                Some((false, ..)) => "error".into(),
                None => "ready".into(),
            },
            bytes: escpos_last.as_ref().map(|(_, b, _, _)| *b),
            ago_sec: escpos_last.as_ref().map(|(_, _, _, a)| *a),
            error: escpos_last.as_ref().and_then(|(_, _, e, _)| e.clone()),
        },
        printer: PrinterStatus {
            name: load(&app).printer,
            mode: "system".into(),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scanner_state_precedence_is_error_then_listening_then_idle() {
        // 打开失败时即便线程仍在也算 error；在收码无错算 listening；其余归 idle
        let decide = |listening: bool, error: Option<String>| -> &'static str {
            if error.is_some() {
                "error"
            } else if listening {
                "listening"
            } else {
                "idle"
            }
        };
        assert_eq!(decide(false, Some("打开失败".into())), "error");
        assert_eq!(decide(true, None), "listening");
        assert_eq!(decide(false, None), "idle");
    }
}
