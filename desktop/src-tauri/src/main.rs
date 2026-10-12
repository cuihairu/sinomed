#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

//! Sinomed 桌面工作站：Tauri 2 薄壳。
//! 业务全在服务端——主窗口直接加载管理端 web 地址；未配置时落 ui/ 引导页做首次配置。
//! 启动后后台查更新（desktop.md D5）：命中新版弹提示，确认后静默下载安装、重启生效。
//! 无人值守运行（desktop.md D6）：单实例锁 + 开机自启 + 崩溃自动重启（看门狗父进程）。

mod commands;
mod config;
mod escpos;
mod peripheral;
mod print;
mod scanner;
mod templates;

use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager, WebviewUrl, WebviewWindowBuilder};

use crate::config::AppConfig;

/// updater 清单地址按配置的更新通道取（config.json update_channel）
const NIGHTLY_ENDPOINT: &str =
    "https://github.com/cuihairu/sinomed/releases/download/nightly/latest.json";
const STABLE_ENDPOINT: &str =
    "https://github.com/cuihairu/sinomed/releases/latest/download/latest.json";

/// 看门狗崩溃重启预算：RESTART_WINDOW 内最多 MAX_RESTARTS 次，超过即放弃（防崩溃风暴循环打转）
const MAX_RESTARTS: usize = 5;
const RESTART_WINDOW: Duration = Duration::from_secs(60);
const RESTART_DELAY: Duration = Duration::from_secs(3);

fn endpoint_for_channel(channel: &str) -> &'static str {
    if channel == "stable" {
        STABLE_ENDPOINT
    } else {
        NIGHTLY_ENDPOINT
    }
}

fn main() {
    // 看门狗形态：直接启动 = 父进程（监管者），带 --supervised = 被监管的真实应用。
    // 父进程不建 tauri app，只负责拉起子进程并按退出码决定重启；正常退出（关窗、
    // 第二实例让位、更新器重启）不拉起，异常退出在预算内自动重启。
    if std::env::args().any(|arg| arg == "--supervised") {
        run_app();
    } else {
        supervise();
    }
}

/// 被监管的应用进程：构建 tauri app 并以退出码收场（0 = 正常，非 0 = 异常）。
fn run_app() {
    // 启动参数优先：--server-url=<地址>（或空格分隔），便于现场调试指向不同环境
    let arg_server_url = config::server_url_from_args(std::env::args());
    let code = match tauri::Builder::default()
        // 单实例锁必须最先注册：二次启动把已有主窗口拉到前台后自己退出
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec![]),
        ))
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .invoke_handler(tauri::generate_handler![
            commands::get_config,
            commands::set_config,
            commands::set_autostart,
            print::print_html,
            escpos::print_escpos,
            scanner::scanner_start,
            scanner::scanner_stop,
            peripheral::peripheral_status
        ])
        .manage(scanner::ScannerState::default())
        .manage(escpos::EscposState::default())
        .setup(move |app| {
            let handle = app.handle().clone();
            let mut cfg = commands::load(&handle);
            if let Some(url) = arg_server_url {
                cfg.server_url = url;
            }
            // 开机自启状态收敛到配置：装机迁移 / 注册表被清等场景自动恢复
            commands::apply_autostart(&handle, cfg.autostart);
            open_main_window(&handle, &cfg)?;
            // 串口扫码枪（D9）：配置了端口即开监听，收码以事件推给当前页面
            if !cfg.scanner_port.is_empty() {
                if let Err(e) = scanner::start(
                    handle.clone(),
                    &handle.state::<scanner::ScannerState>(),
                    cfg.scanner_port.clone(),
                    None,
                ) {
                    eprintln!("[scanner] {e}");
                }
            }
            tauri::async_runtime::spawn(templates::refresh(handle.clone()));
            tauri::async_runtime::spawn(check_for_updates(handle));
            Ok(())
        })
        .run(tauri::generate_context!())
    {
        Ok(()) => 0,
        Err(e) => {
            eprintln!("Sinomed 工作站启动失败：{e}");
            1
        }
    };
    std::process::exit(code);
}

/// 看门狗父进程：循环拉起 --supervised 子进程并等待；成功退出则收班，
/// 异常退出在预算内重启、超预算放弃（止损 + 留日志），收终止信号时连子进程一起带走。
fn supervise() -> ! {
    let exe = std::env::current_exe().unwrap_or_else(|e| {
        eprintln!("看门狗拿不到可执行文件路径：{e}");
        std::process::exit(1);
    });
    install_term_guard();
    let mut crashes: Vec<Instant> = Vec::new();
    let mut child = spawn_supervised(&exe);
    loop {
        // 短轮询等待：给终止信号留出检查点（不阻塞在 waitpid 上）
        let status = loop {
            if terminal_shutdown() {
                eprintln!("[supervisor] 收到终止信号，带走子进程后退出");
                let _ = child.kill();
                let _ = child.wait();
                std::process::exit(0);
            }
            match child.try_wait() {
                Ok(Some(status)) => break status,
                Ok(None) => std::thread::sleep(POLL_INTERVAL),
                Err(e) => {
                    eprintln!("[supervisor] 等待子进程失败：{e}");
                    std::process::exit(1);
                }
            }
        };
        if status.success() {
            eprintln!("[supervisor] 子进程正常退出，看门狗收班");
            std::process::exit(0);
        }
        crashes.push(Instant::now());
        crashes.retain(|t| t.elapsed() <= RESTART_WINDOW);
        if crash_budget_exceeded(&crashes) {
            eprintln!(
                "[supervisor] {} 秒内已崩溃重启 {} 次，超出预算，放弃重启（{status}）",
                RESTART_WINDOW.as_secs(),
                crashes.len()
            );
            std::process::exit(1);
        }
        eprintln!(
            "[supervisor] 子进程异常退出（{status}），{} 秒后自动重启（{} 秒内第 {} 次）",
            RESTART_DELAY.as_secs(),
            RESTART_WINDOW.as_secs(),
            crashes.len()
        );
        std::thread::sleep(RESTART_DELAY);
        child = spawn_supervised(&exe);
    }
}

fn spawn_supervised(exe: &std::path::Path) -> std::process::Child {
    std::process::Command::new(exe)
        .arg("--supervised")
        .spawn()
        .unwrap_or_else(|e| {
            eprintln!("[supervisor] 拉起子进程失败：{e}");
            std::process::exit(1);
        })
}

/// 子进程退出状态的轮询间隔
const POLL_INTERVAL: Duration = Duration::from_millis(200);

#[cfg(unix)]
mod term_guard {
    use std::sync::atomic::{AtomicBool, Ordering};

    /// 终止信号到达标记（SIGTERM / SIGINT），由信号处理器置位
    static TERMINATING: AtomicBool = AtomicBool::new(false);

    extern "C" fn on_term(_sig: libc::c_int) {
        TERMINATING.store(true, Ordering::SeqCst);
    }

    /// 注册终止信号处理器为置标记（覆盖默认的直接杀死行为，由监管循环决定退出）
    pub fn install() {
        unsafe {
            libc::signal(libc::SIGTERM, on_term as *const () as libc::sighandler_t);
            libc::signal(libc::SIGINT, on_term as *const () as libc::sighandler_t);
        }
    }

    /// 是否已收到终止信号
    pub fn requested() -> bool {
        TERMINATING.load(Ordering::SeqCst)
    }
}

/// unix 下父进程是否收到终止信号（其它平台恒 false，进程终止走任务管理器整组结束）
#[cfg(unix)]
fn terminal_shutdown() -> bool {
    term_guard::requested()
}

#[cfg(not(unix))]
fn terminal_shutdown() -> bool {
    false
}

#[cfg(unix)]
fn install_term_guard() {
    term_guard::install();
}

#[cfg(not(unix))]
fn install_term_guard() {}

/// 崩溃预算判定：窗口内的崩溃次数超预算即放弃重启（纯函数，单测覆盖）。
/// `crashes` 为含本次在内的崩溃时刻列表。
fn crash_budget_exceeded(crashes: &[Instant]) -> bool {
    crashes
        .iter()
        .filter(|t| t.elapsed() <= RESTART_WINDOW)
        .count()
        > MAX_RESTARTS
}

/// 已配置 → 直接加载远程 web 地址；未配置 → 引导页（ui/index.html）做首次配置。
fn open_main_window(app: &AppHandle, cfg: &AppConfig) -> tauri::Result<()> {
    let url = match cfg.server_url() {
        Some(url) => WebviewUrl::External(url),
        None => WebviewUrl::App("index.html".into()),
    };
    WebviewWindowBuilder::new(app, "main", url)
        .title("Sinomed 工作站")
        .inner_size(cfg.window_width as f64, cfg.window_height as f64)
        .min_inner_size(960.0, 600.0)
        .build()?;
    Ok(())
}

/// 后台查更新：失败只记日志不打扰使用（无 Release / 离线均属常态）；命中新版弹窗确认后
/// 下载安装并重启（NSIS 安装器接管，重启即新版）。
async fn check_for_updates(app: AppHandle) {
    use tauri_plugin_updater::UpdaterExt;

    // 等窗口先起来，别跟首屏抢带宽
    tauri::async_runtime::spawn_blocking(|| std::thread::sleep(std::time::Duration::from_secs(5)))
        .await
        .ok();

    let channel = commands::load(&app).update_channel;
    let endpoint = endpoint_for_channel(&channel);
    let endpoint_url: tauri::Url = endpoint.parse().expect("更新端点 URL");
    let updater = match app
        .updater_builder()
        .endpoints(vec![endpoint_url])
        .and_then(|builder| builder.build())
    {
        Ok(updater) => updater,
        Err(e) => {
            eprintln!("[updater] 构建更新器失败（通道 {channel}）：{e}");
            return;
        }
    };

    match updater.check().await {
        Ok(Some(update)) => {
            eprintln!(
                "[updater] 发现新版本 {}（当前 {}）",
                update.version,
                app.package_info().version
            );
            use tauri_plugin_dialog::{DialogExt, MessageDialogButtons};
            let confirmed = app
                .dialog()
                .message(format!(
                    "发现新版本 {}（当前 {}），立即更新？\n更新将下载并自动重启工作站。",
                    update.version,
                    app.package_info().version
                ))
                .title("Sinomed 工作站")
                .buttons(MessageDialogButtons::OkCancelCustom(
                    "立即更新".into(),
                    "下次再说".into(),
                ))
                .blocking_show();
            if !confirmed {
                eprintln!("[updater] 用户选择暂不更新");
                return;
            }
            let mut downloaded: usize = 0;
            match update
                .download_and_install(
                    |chunk, total| {
                        downloaded += chunk;
                        if let Some(total) = total {
                            eprintln!("[updater] 下载 {downloaded}/{total} 字节");
                        }
                    },
                    || eprintln!("[updater] 下载完成，开始安装"),
                )
                .await
            {
                Ok(()) => {
                    eprintln!("[updater] 安装完成，重启生效");
                    app.restart();
                }
                Err(e) => eprintln!("[updater] 下载/安装失败：{e}"),
            }
        }
        Ok(None) => eprintln!("[updater] 已是最新版本（通道 {channel}）"),
        Err(e) => eprintln!("[updater] 检查更新失败（通道 {channel}）：{e}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crash_budget_allows_up_to_max_restarts() {
        // 预算内：MAX_RESTARTS 次崩溃仍可重启，再多一次即放弃
        let crashes: Vec<Instant> = (0..MAX_RESTARTS).map(|_| Instant::now()).collect();
        assert!(!crash_budget_exceeded(&crashes));
        let mut over: Vec<Instant> = (0..=MAX_RESTARTS).map(|_| Instant::now()).collect();
        assert!(crash_budget_exceeded(&over));
        // 窗口外的旧崩溃不计入预算
        over.clear();
        over.push(Instant::now() - RESTART_WINDOW - Duration::from_secs(1));
        assert!(!crash_budget_exceeded(&over));
    }

    #[test]
    fn supervised_flag_detection() {
        // 带参形态与看门狗/子进程分流口径一致
        let args = ["app".to_string(), "--supervised".to_string()];
        assert!(args.iter().any(|a| a == "--supervised"));
        let args = ["app".to_string()];
        assert!(!args.iter().any(|a| a == "--supervised"));
    }
}
