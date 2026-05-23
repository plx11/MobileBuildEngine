# MobileBuildEngine - GEMINI.md

## 專案概覽
MobileBuildEngine 是一個原生的 Android 編譯引擎，旨在繞過 Android 核心沙箱限制，直接在 App 進程空間內調度 Android 核心編譯工具鏈 (`aapt2`, `d8`, `r8`, `apksigner`, `zipalign`)。

## 核心技術棧
- **語言**: Kotlin (1.9.0)
- **架構**: Android (minSdk 26, targetSdk 34)
- **UI 框架**: Material3, Jetpack Compose
- **架構模式**: MVVM
- **依賴管理**: Gradle, androidx.lifecycle, androidx.core-ktx

## 編譯與執行命令
該專案使用標準 Gradle 構建系統：

- **構建 App**: `./gradlew assembleDebug`
- **運行測試**: `./gradlew test`
- **清理專案**: `./gradlew clean`
- **發佈構建**: `./gradlew assembleRelease`

## 開發約定與規範
- **代碼組織**:
  - `core/`: 存放編譯核心邏輯 (`BuildWorker`, `CompilationPipeline`, `ToolchainManager`)
  - `ui/`: 存放 UI 組件與 `MainActivity`
- **品質要求**:
  - 嚴禁使用「簡化示意」、「架構示意」或 `TODO` 註解來跳過核心實作。
  - 所有外部二進位調用必須包含詳細的錯誤流捕獲與退出代碼檢查 (`exitValue() == 0`)。
- **Kotlin 約定**: 優先使用 Kotlin 特性編寫生產級程式碼，確保類型安全與空安全。
- **版本控制**: 使用 Git 進行開發，提交訊息應簡明且描述清楚「為什麼」進行修改。

## 外部工具鏈要求
所有新增的編譯功能必須透過 `ToolchainManager` 進行初始化與權限校驗，確保在 Android 環境下具備必要的執行權限。
