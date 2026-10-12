//! 扫码枪串口模式（desktop.md D9）：HID 键盘仿真不可用时的兜底。
//! 壳持一条读线程按行收码，收到即向所有窗口发 `scanner-code` 事件；
//! web 页（含远程加载的管理端）经 `__TAURI__.event.listen` 订阅后走既有定位流程，
//! 页面侧无需 invoke 权限。端口来自 config.scanner_port，启动即拉起，空 = 不启用。

use std::io::Read;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use tauri::{AppHandle, Emitter, State};

/// 串口扫码枪读码线程的状态（scanner_start / scanner_stop 共享）
#[derive(Default)]
pub struct ScannerState {
    stop: Mutex<Option<Arc<AtomicBool>>>,
    /// 运行时快照（peripheral_status 只读，读线程持有克隆）：当前端口 + 是否在读 + 最近错误
    runtime: Arc<Mutex<ScannerRuntime>>,
}

/// 读线程运行时快照：port = 监听端口（None = 未开）；listening = 串口已打开且在收码；
/// error = 打开 / 读取失败信息（None = 无错）
#[derive(Default)]
struct ScannerRuntime {
    port: Option<String>,
    listening: bool,
    error: Option<String>,
}

impl ScannerState {
    /// 只读快照（peripheral_status 用）：端口、监听态、最近错误
    pub(crate) fn snapshot(&self) -> (String, bool, Option<String>) {
        let runtime = self.runtime.lock().expect("扫码枪状态锁");
        (
            runtime.port.clone().unwrap_or_default(),
            runtime.listening,
            runtime.error.clone(),
        )
    }
}

/// 从缓冲里切出整行（\n 或 \r 结尾），返回切出的码值并收缩缓冲；残行留在缓冲等下一包
fn extract_lines(buf: &mut Vec<u8>) -> Vec<String> {
    let mut codes = Vec::new();
    while let Some(pos) = buf.iter().position(|b| *b == b'\n' || *b == b'\r') {
        let line: Vec<u8> = buf.drain(..=pos).collect();
        let code = String::from_utf8_lossy(&line[..line.len() - 1]).trim().to_string();
        if !code.is_empty() {
            codes.push(code);
        }
    }
    codes
}

/// 停掉在读码线程（置停止位即可，线程 100ms 内自行退出并关端口）
fn stop_running(state: &ScannerState) {
    if let Some(stop) = state.stop.lock().expect("扫码枪状态锁").take() {
        stop.store(true, Ordering::Relaxed);
    }
    *state.runtime.lock().expect("扫码枪状态锁") = ScannerRuntime::default();
}

/// 启动串口扫码枪监听：已有线程在跑则先停（换端口场景）；打开失败发 `scanner-error` 事件
pub(crate) fn start(
    app: AppHandle,
    state: &ScannerState,
    port: String,
    baud: Option<u32>,
) -> Result<(), String> {
    stop_running(state);
    let stop = Arc::new(AtomicBool::new(false));
    *state.stop.lock().map_err(|e| format!("扫码枪状态锁：{e}"))? = Some(stop.clone());
    let runtime = state.runtime.clone();
    *runtime.lock().map_err(|e| format!("扫码枪状态锁：{e}"))? = ScannerRuntime {
        port: Some(port.clone()),
        listening: false,
        error: None,
    };
    let baud = baud.unwrap_or(9600);
    std::thread::spawn(move || read_loop(app, port, baud, stop, runtime));
    Ok(())
}

/// 同上，命令入口（setup 启动自启用 start 直连）
#[tauri::command]
pub fn scanner_start(
    app: AppHandle,
    state: State<'_, ScannerState>,
    port: String,
    baud: Option<u32>,
) -> Result<(), String> {
    start(app, &state, port, baud)
}

/// 停止串口扫码枪监听
#[tauri::command]
pub fn scanner_stop(state: State<'_, ScannerState>) -> Result<(), String> {
    stop_running(&state);
    Ok(())
}

/// 读码循环：100ms 超时轮询（给停止位留检查点），按行切码发事件；读错误上报后退出
fn read_loop(
    app: AppHandle,
    port: String,
    baud: u32,
    stop: Arc<AtomicBool>,
    runtime: Arc<Mutex<ScannerRuntime>>,
) {
    let mut serial = match serialport::new(&port, baud)
        .timeout(Duration::from_millis(100))
        .open()
    {
        Ok(serial) => serial,
        Err(e) => {
            let msg = format!("打开扫码枪串口 {port} 失败：{e}");
            eprintln!("[scanner] {msg}");
            set_runtime(&runtime, &port, false, Some(msg));
            return;
        }
    };
    set_runtime(&runtime, &port, true, None);
    let _ = app.emit("scanner-status", "listening");
    let mut buf: Vec<u8> = Vec::new();
    let mut chunk = [0u8; 256];
    while !stop.load(Ordering::Relaxed) {
        match serial.read(&mut chunk) {
            Ok(n) if n > 0 => {
                buf.extend_from_slice(&chunk[..n]);
                for code in extract_lines(&mut buf) {
                    let _ = app.emit("scanner-code", code);
                }
            }
            Ok(_) => {}
            // read 走 std::io::Read，超时以 io::ErrorKind::TimedOut 表达
            Err(e) if e.kind() == std::io::ErrorKind::TimedOut => {}
            Err(e) => {
                let msg = format!("读扫码枪串口失败：{e}");
                eprintln!("[scanner] {msg}");
                let _ = app.emit("scanner-error", msg.clone());
                set_runtime(&runtime, &port, false, Some(msg));
                break;
            }
        }
    }
    set_runtime(&runtime, &port, false, None);
    let _ = app.emit("scanner-status", "stopped");
}

/// 更新运行时快照（读线程用；端口与错误一并写，锁持有时间极短）
fn set_runtime(runtime: &Arc<Mutex<ScannerRuntime>>, port: &str, listening: bool, error: Option<String>) {
    let mut guard = runtime.lock().expect("扫码枪状态锁");
    guard.port = Some(port.to_string());
    guard.listening = listening;
    guard.error = error;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_complete_lines_and_keeps_partial() {
        let mut buf = b"13900002222\nABC-XY".to_vec();
        let codes = extract_lines(&mut buf);
        assert_eq!(codes, vec!["13900002222".to_string()]);
        assert_eq!(buf, b"ABC-XY");
        // 补上结尾符后切出残行
        buf.push(b'\r');
        assert_eq!(extract_lines(&mut buf), vec!["ABC-XY".to_string()]);
        assert!(buf.is_empty());
    }

    #[test]
    fn ignores_empty_and_crlf_lines() {
        let mut buf = b"\r\n\n13900002222\r\n".to_vec();
        assert_eq!(extract_lines(&mut buf), vec!["13900002222".to_string()]);
        assert!(buf.is_empty());
    }

    #[test]
    fn trims_surrounding_whitespace() {
        let mut buf = b"  123  \n".to_vec();
        assert_eq!(extract_lines(&mut buf), vec!["123".to_string()]);
    }
}
