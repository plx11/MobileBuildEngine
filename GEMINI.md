# MobileBuildEngine - GEMINI.md

## 專案概覽
MobileBuildEngine 是一個原生的 Android 編譯引擎，旨在繞過 Android 核心沙箱限制，直接在 App 進程空間內調度 Android 核心編譯工具鏈 (`aapt2`, `d8`, `r8`, `apksigner`, `zipalign`)。
本工具定位為「純粹編譯引擎」，不包含程式碼編輯功能，提供 UI 介面供使用者輸入專案路徑（本地/GitHub），並支援「一鍵編譯」與實時編譯日誌顯示。

## 核心技術棧
- **語言**: Kotlin
- **介面技術**: Android View (XML) 或 Jetpack Compose (規劃中)
- **架構模式**: MVVM，編譯任務由 `BuildEngineController` 透過 `WorkManager` 調度
- **核心工具**: `AAPT2`, `D8`, `R8`, `apksigner`, `zipalign`
- **依賴管理**: Maven 倉庫解析，支援 `.jar`/`.aar` 遞迴下載與快取

## UI 與交互規範
- **輸入端**: 支援本地路徑輸入與 GitHub Repository URL 解析。
- **控制端**: 提供「一鍵編譯」按鈕，觸發 `BuildEngineController` 工作流。
- **監控端**: 提供 ScrollView 封裝的 Log TextView，實時輸出 `BuildEngineController` 的編譯日誌。
- **輸出端**: 編譯成功後提示 APK 存放路徑。

## 開發約定
- **模組封裝**: 編譯邏輯位於 `com.mobilebuildengine.app.core`，UI 邏輯位於 `com.mobilebuildengine.app.ui`。
- **Kotlin 編譯要求**: 所有專案原始碼必須優先經由 `KotlinCompilerEngine` 調用 `kotlinc` 編譯為 `.class` 文件，隨後再由 Java 相關工具鏈進行後續處理。
- **錯誤處理**: 所有調用外部二進位的過程必須處理 `ProcessBuilder` 錯誤流與異常。
- **權限管理**: `ToolchainManager` 確保執行權限。
- **依賴規範**: 優先使用 `MavenDependencyManager`。
