# MobileBuildEngine

MobileBuildEngine 是一個高效、原生的 Android 編譯引擎，專為在 Android 設備沙箱環境內執行自動化 APK 構建而設計。本專案致力於將繁瑣的 Android 編譯鏈條簡化為一鍵操作，並提供工業級的穩定性與擴展性。

## 核心模組與技術架構

本引擎由以下六大核心模組組成，確保了編譯流程的穩定與高效：

1. **資源打包 (AAPT2 Processor)**:
   - 負責資源編譯 (Compile)、連結 (Link) 及 APK 對齊 (Zipalign)。確保資源 ID 的正確性與安裝套件的規範。
2. **Java 編譯 (ECJ Core)**:
   - 整合 Eclipse Java Compiler (ECJ)，無須完整 JDK 即可在手機沙箱內將 `.java` 轉換為 `.class`。
3. **字節碼轉換 (D8/R8 Dexer)**:
   - 處理 Java Bytecode 到 DEX 的轉換，支援多 DEX 配置與 R8 混淆/優化規則解析，以精簡產出 APK 體積。
4. **Maven 下載與版本仲裁中心**:
   - 支援遞迴依賴下載，具備自動重試機制、SHA-1 完整性校驗與版本號仲裁演算法，自動解決多庫間的版本衝突。
5. **標準化配置解析 (BuildManifest)**:
   - 支援結構化 JSON 配置與傳統 Gradle 腳本掃描，實現編譯參數的標準化協議。
6. **Android 背景服務與 UI 交互**:
   - 採用 MVVM 架構，利用背景執行緒處理耗時編譯，配合進度條與實時日誌系統，實現良好的開發者交互。

## 核心亮點

- **工業級穩健性**: 具備任務隔離機制 (WorkspaceManager)，防止多工衝突。
- **高容錯設計**: 下載損壞檔案自修復、預設 Debug 簽名備援機制。
- **一鍵式工作流**: 從專案來源識別到 APK 最終產出，流程高度自動化。

## 使用說明

1. **環境配置**: 放置必要的原生工具鏈於 `assets/binaries`。
2. **定義專案**: 可選擇輸入專案路徑或提供符合協議的 `build.json` 檔案。
3. **編譯**: 點擊「一鍵編譯」，系統自動執行全流水線作業。
4. **結果**: 查看 `tvLogs` 即時日誌，成功後將顯示 APK 生成路徑。

## 開發者指南

- **模組擴展**: 所有核心邏輯封裝於 `com.mobilebuildengine.app.core`，維護單一職責原則。
- **故障排查**: 透過 `BuildLogger` 介面獲取結構化錯誤報告。

---
*MobileBuildEngine - 為 Android 原生編譯提供工業級解決方案。*
