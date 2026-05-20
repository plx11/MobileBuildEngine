# MobileBuildEngine

MobileBuildEngine 是一個在 Android 系統內部原生運行的 APK 編譯打包引擎。它避開了傳統 Linux 容器 (PRoot/Termux) 的沙箱限制，直接在 App 進程空間內調度 Android 核心編譯工具鏈，實現真正的原生自動化 APK 構建。

## 已實現的核心功能
- **原生執行引擎**: 實作 `ToolchainManager`，自動部署並配置 `aapt2`, `d8`, `r8`, `apksigner`, `zipalign` 等二進位工具的執行環境。
- **自動化依賴解析**: 實作 `MavenDependencyManager`，支援從 Maven Central 自動下載並處理遞迴依賴，構建編譯所需的 Classpath。
- **完整編譯流水線**: 整合原子化的 `ResourceCompiler` (資源處理)、`D8Dexer` (DEX 生成)、`R8Optimizer` (代碼優化與混淆) 及 `ApkPackager` (對齊封裝)。
- **背景任務處理**: 基於 `WorkManager` 與 `BuildEngineController`，實現了無須 UI 阻塞的全自動化背景編譯管線。
- **工業級簽名**: 整合 `apksigner`，支援自動化 Debug 簽名流程。

## 專案進度與架構邏輯
- **核心代碼層**: 已完成所有底層調度邏輯，包含錯誤流監控、進程同步處理與異常恢復機制。
- **生產級封裝**: 具備發佈級 APK 生成能力 (R8 優化 + Zipalign 對齊)。

## 未來計畫 (Roadmap)
- **多進程編譯優化**: 引入編譯快取系統，利用多核心並行處理資源編譯與 DEX 生成任務。
- **進階依賴庫處理**: 進一步完善 `.aar` 格式的解包與 Native (`.so`) 函式庫提取邏輯。
- **IDE 整合層**: 開發一組 API 接口，供前端 UI 直接呼叫，並獲取結構化的編譯日誌 (JSON 格式)。
- **SDK 模擬器擴展**: 構建更完整的 `android.jar` 版本對照表，以支援從 API 21 到最新版本的 SDK 編譯兼容性。
- **斷點重啟機制**: 針對大型專案，實作基於任務快取的增量編譯，大幅縮減二次編譯時間。
