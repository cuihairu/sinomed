use serde::{Deserialize, Serialize};

/// config.json（app_config_dir 下），字段见 desktop.md「数据模型」：
/// 服务端地址、窗口尺寸、默认打印机（D2 用）、更新通道（D5 用）。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AppConfig {
    /// 管理端 web 地址（如 http://192.168.1.10/）；空串 = 未配置，主窗口落引导页
    #[serde(default)]
    pub server_url: String,
    #[serde(default = "default_width")]
    pub window_width: u32,
    #[serde(default = "default_height")]
    pub window_height: u32,
    /// 默认打印机名称（P0 打印走系统对话框，该字段仅作预选记录）
    #[serde(default)]
    pub printer: String,
    /// 更新通道：nightly / stable（D5 updater 用）
    #[serde(default = "default_channel")]
    pub update_channel: String,
    /// 开机自启（D6 无人值守）：配置为唯一事实来源，启动时收敛到系统自启项
    #[serde(default)]
    pub autostart: bool,
    /// 串口扫码枪（D9 兜底）：如 /dev/ttyUSB0 或 COM3；空 = 不启用（走 HID 键盘仿真）
    #[serde(default)]
    pub scanner_port: String,
}

fn default_width() -> u32 {
    1360
}

fn default_height() -> u32 {
    832
}

fn default_channel() -> String {
    "nightly".into()
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            server_url: String::new(),
            window_width: default_width(),
            window_height: default_height(),
            printer: String::new(),
            update_channel: default_channel(),
            autostart: false,
            scanner_port: String::new(),
        }
    }
}

impl AppConfig {
    /// server_url 非空时解析为可导航地址；非法值按未配置处理（回落引导页）。
    pub fn server_url(&self) -> Option<tauri::Url> {
        if self.server_url.is_empty() {
            return None;
        }
        tauri::Url::parse(&self.server_url)
            .ok()
            .filter(|u| matches!(u.scheme(), "http" | "https"))
    }

    /// 引导页保存前校验：必须是 http(s) 地址。
    pub fn validate(&self) -> Result<(), String> {
        if !self.server_url.is_empty() && self.server_url().is_none() {
            return Err("服务端地址必须是 http:// 或 https:// 开头的完整地址".into());
        }
        if self.window_width < 640 || self.window_height < 480 {
            return Err("窗口尺寸过小（最小 640×480）".into());
        }
        Ok(())
    }
}

/// 解析启动参数 `--server-url=<地址>`（亦支持空格分隔），优先级高于 config.json。
pub fn server_url_from_args<I: Iterator<Item = String>>(args: I) -> Option<String> {
    let mut next_is_url = false;
    for arg in args {
        if next_is_url {
            return Some(arg);
        }
        if let Some(value) = arg.strip_prefix("--server-url=") {
            return Some(value.to_string());
        }
        if arg == "--server-url" {
            next_is_url = true;
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_points_to_setup_page() {
        let cfg = AppConfig::default();
        assert_eq!(cfg.server_url, "");
        assert!(cfg.server_url().is_none());
        cfg.validate().unwrap();
    }

    #[test]
    fn server_url_accepts_http_and_https_only() {
        let mut cfg = AppConfig::default();
        cfg.server_url = "http://192.168.1.10:8000/".into();
        assert!(cfg.server_url().is_some());
        cfg.server_url = "https://clinic.example.com/".into();
        assert!(cfg.server_url().is_some());
        cfg.server_url = "ftp://x".into();
        assert!(cfg.server_url().is_none());
        cfg.server_url = "不是地址".into();
        assert!(cfg.server_url().is_none());
        assert!(cfg.validate().is_err());
    }

    #[test]
    fn serde_roundtrip_keeps_defaults() {
        let cfg: AppConfig = serde_json::from_str(r#"{"server_url":"http://x/"}"#).unwrap();
        // 默认窗口尺寸与 default_width()/default_height() 一致（1360×832）
        assert_eq!(cfg.window_width, default_width());
        assert_eq!(cfg.window_height, default_height());
        assert_eq!(cfg.update_channel, "nightly");
        assert!(!cfg.autostart);
        let text = serde_json::to_string(&cfg).unwrap();
        assert_eq!(cfg, serde_json::from_str(&text).unwrap());
    }

    #[test]
    fn arg_override_forms() {
        let args = || ["app".to_string()].into_iter();
        assert_eq!(server_url_from_args(args()), None);
        assert_eq!(
            server_url_from_args(
                ["app".to_string(), "--server-url=http://a/".to_string()].into_iter()
            ),
            Some("http://a/".into())
        );
        assert_eq!(
            server_url_from_args(
                ["app".to_string(), "--server-url".to_string(), "http://b/".to_string()].into_iter()
            ),
            Some("http://b/".into())
        );
    }
}
