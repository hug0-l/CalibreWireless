# CalibreWireless — Android 無線裝置同步客戶端 設計規格

日期：2026-09-19
狀態：已與使用者逐段確認（§1 架構/協議、§2 UI/錯誤/驗收）

## 1. 目的與背景

使用者在 Mac 上用 calibre 管理書庫（`/Applications/calibre.app`，書庫 `~/calibre 書庫`），
希望一支自己寫的 Android app 透過 calibre 內建的「Android 無線裝置存取」協議，
讓手機與書庫之間同步書籍（下載、上傳、刪除）。

環境事實：

- NAS (hs-unraid) 上的 `calibre.hug0nef.xyz:8083` 是 **calibre-web-nextgen**，不講無線裝置協議，與本專案無關
- 伺服器端固定為 **Mac 上的 calibre desktop**（使用者手動啟動「無線裝置存取」，預設 port 8081）
- 測試機：Xiaomi Mi MIX 2S（polaris, LineageOS, API 35），已接 adb
- 書庫現況極小（1 本書），性能不是約束

## 2. 範圍（v1）

### 做

- 配對：IP + Port + 裝置名 + 4 位 authkey → `/register` 換 `user_key` 持久化
- 書庫頁：`/list` 全量清單、客戶端搜尋（標題/作者/tags）、下載、從書庫刪除
- 裝置頁：掃描使用者選定的 SAF 目錄列出既有電子書、上傳到書庫（`/upload`+`/import`）、從手機刪除；另有 SAF 多檔選取上傳
- 傳輸佇列：前景协程、逐檔進度、可取消
- 兩端比對徽章：app 經手的用 `library_id → 相對路徑` 對照表；手動拷入的退化為檔名常態化比對，配不上就只顯示「裝置端」，絕不誤判為可刪

### 不做（與加回時機）

- 內建閱讀器 — 交給外部 reader（讀 SAF 目錄）
- 閱讀進度同步 — 需要自訂欄位＋reader 配合，等傳輸流穩定後另開需求
- 封面縮圖 — 協議無單本封面端點，SKIP
- 雙向 true sync（含刪除傳播）— 誤刪風險，用三個月再說
- 背景/自動同步 — Mac 非 24/7，無意義
- mDNS 自動發現（`_calibre-wireless-device._tcp` + NsdManager）— 二期候選，屆時配對畫面多一顆「自動探索」鈕即可

## 3. 技術決策

| 項目 | 決定 |
|------|------|
| 平台 | Android 原生，單一 app 模組 |
| 語言/UI | Kotlin + Jetpack Compose (Material 3) |
| 網路 | OkHttp + kotlinx.serialization + Coroutines |
| 儲存 | DataStore（配對資料、對照表）；不用 sqlite |
| minSdk / targetSdk | 26 / 35 |
| 分發 | sideload，debug 或自簽 release；不上 Play |
| UI 語言 | 繁體中文 |

## 4. 協議客戶端（核心不確定性所在）

**第 0 步（寫任何 Kotlin 之前）**：從 `/Applications/calibre.app` 解出
`calibre/devices/wireless/` 的 Python 源碼核實全部端點與欄位；在 Mac 實際啟動無線服務，
用 curl 對每個端點錄 request/response 存為 fixture（進 repo `fixtures/`）。
下表是實作依據的輪廓，**以源碼核實結果為準**：

| 端點 | 方法 | 說明 |
|------|------|------|
| `/register` | POST JSON `{device_name, authkey}` | 回 `{user_key}`；此後全部請求走 HTTP Basic（device_name:user_key） |
| `/list` | POST | 回 `stores[]` + 書目（library_id、title、author、tags、formats、timestamp…以 fixture 為準） |
| `/` | GET `?storage_id=&id_list=[..]` | 下載回 **ZIP**，解壓進 SAF 目錄 |
| `/upload` | POST multipart | 檔案進 calibre 暫存區（欄位名以源碼為準） |
| `/import` | POST JSON | 暫存檔進書庫：新書出新條目；既有 library_id 則加格式 |
| `/delete` | POST JSON `{library_id, storage_id, lpath}` | 從書庫刪 |

除錯加成：若 `calibre-debug` 能在無 GUI 下起無線服務（第 0 步驗證），
協定層就能在 CI/本機全自動化測試；不能則單元測試照跑 fixture，E2E 留在實機清單。

## 5. 架構

```
app/src/main/java/.../calibrewireless/
  proto/CalibreWirelessClient.kt   // 唯一對協議出口：suspend 方法 per 端點
  proto/Models.kt                  // 請求/回應 data class（僅取自 fixture 出現的欄位）
  data/DeviceBooks.kt              // SAF 掃描 + 正規化檔名 + 對照表讀寫
  data/PairingStore.kt             // DataStore: host, port, deviceName, userKey, 選定目錄 tree URI, id→path 表
  ui/PairScreen.kt
  ui/SyncScreen.kt                 // 書庫/裝置雙分頁 + 搜尋 + 動作面板 + banner
  ui/QueueUi.kt                    // 佇列列（ViewModel: StateFlow<Job>）
  CalibreWirelessApp.kt            // NavHost
```

依賴方向：`ui → data → proto`。proto 不認得 Compose；DeviceBooks 不認得網路。

## 6. 主要流程

1. **配對**：PairScreen 表單 → `/register` → 存 key → SyncScreen
2. **下載**：選書（可多選）→ 佇列逐本 `GET /?id_list=[id]` → 解 ZIP（撞名加 `-2`，不覆蓋）→ 寫 tree URI → 更新對照表
3. **上傳**：裝置頁選檔或 SAF picker → `/upload` → `/import` → 成功後刷新 `/list`，失敗 toast + 列入可重試失敗項
4. **刪除**：書庫端 `/delete`；裝置端 SAF `DocumentsContract.deleteDocument`。兩邊刪除都是單邊、都要確認对话框
5. **重進 app**：用存好的 key 直接 `/list`；401 才回 PairScreen（保留 IP）

## 7. 錯誤處理

| 狀況 | 行為 |
|------|------|
| 401/403 | 「key 已失效（calibre 端重啟過？）」→ 跳 PairScreen，IP 欄保留 |
| 連線拒絕/逾時 | 紅 banner「Mac 端無線裝置服務有開嗎？」+ 重試 |
| `success:false`（如 /import） | 顯示 error 欄位訊息；該檔進失敗清單可一鍵重試 |
| ZIP 解壓撞名 | `-2` 後綴 |
| SAF 權限失效（ROM 回收） | 提示重選目錄，對照表保留 |

## 8. 測試

- **單元（JVM）**：CalibreWirelessClient 對 OkHttp MockWebServer + `fixtures/` 真-recorded 報文；register 成/敗、401、list 解析、upload+import 串接、delete、zip 解壓與撞名、檔名正規化比對
- **實機 E2E 清單**（adb 裝機 + 手動執行，結果記錄進 repo）：
  1. 配對成功，重進 app 免再輸 authkey
  2. 書庫頁正確列出 Mac 書庫全部書（含搜尋）
  3. 下載一本 → SAF 目錄出現、外部 reader 打得開
  4. 上傳一本新書 → Mac calibre 出現，metadata/封面正確
  5. 兩側各刪一本，calibre/手機實際消失
  6. 拔網重試 → banner 出、recovery 正常
- 驗收 = 全過 1–6。

## 9. 里程碑

1. **M0 協議核實**：源碼 + curl fixtures（產出：fixtures/ + 端點備忘）
2. **M1 客戶端 + 測試**：proto 層 + MockWebServer 綠燈
3. **M2 UI**：Pair + Sync + Device 掃描 + 佇列
4. **M3 實機 E2E**：§8 清單 1–6 過，交你日常用
M1 前不需要 Android Studio 級工具鏈之外的任何東西（Gradle + JDK + adb 全在位）。
