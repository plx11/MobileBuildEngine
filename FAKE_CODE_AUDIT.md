# 虛假代碼 / 未實作功能檢查報告

日期：2026-05-23

## 結論
此專案存在**部分「聲稱完整實作，但實際上是簡化/硬編碼/假設流程」**的問題，主要集中在編譯流程協調層。

## 發現

1. `BuildEngineController.executeFullBuild` 在偵測到 `build.gradle` 存在時，沒有實際解析依賴，而是硬編碼下載 `com.example:lib:1.0.0`。
   - 這屬於「流程看似完整、實際未接上真實輸入」的典型假實作。

2. `CompilationPipeline.execute` 的 APK 打包階段，對 DEX 輸出採用「假設一定有 `classes.dex`」的前提。
   - 程式碼註解已明確寫出「假設」與「暫時拋出異常」，代表此處仍非完整實作（尤其多 dex 場景）。

3. 專案同時存在舊版與新版依賴解析器：
   - `DependencyResolver` 仍是簡化版（只抓 `jar`，未完整處理 `aar` 流程）；
   - `EnhancedDependencyResolver` 才是較完整版本。
   - 這不一定是 bug，但容易造成「看起來支援完整依賴，實際路徑取決於呼叫端」的落差。

## 風險等級（主觀）
- BuildEngineController 依賴硬編碼：**高**
- CompilationPipeline 的單一 `classes.dex` 假設：**中-高**
- 舊版簡化解析器仍保留：**中**

## 建議
1. 將 `BuildEngineController` 依賴解析改為與 `CompilationPipeline` 一致：使用 `GradleParser + EnhancedDependencyResolver`。
2. 明確處理 D8 多 dex 輸出：
   - `ApkPackager` 支援目錄/多 dex；或
   - 在 pipeline 補齊 dex 收集與打包規則。
3. 若 `DependencyResolver` 已被新架構取代，建議標記 deprecated 或移除，避免誤用。
