# CalibreWireless — Android 無線裝置（smart-device）客戶端 設計規格 v2

日期：2026-09-19
狀態：v2 重寫。v1（HTTP /register 假想協議）經源碼核實作廢：calibre「Android 無線裝置」
實為 JSON-over-TCP 的 smart-device 協議，calibre 單向鎖步驅動，手機=被操作的虛擬裝置。
使用者已在實測方案 A/B/C 後明確選 C（純裝置模擬，KOReader 同款交互）。

## 1. 目的與背景

在手機上跑一支 app，向 Mac 上的 calibre desktop 模擬成一台「無線裝置」。
之後所有書本往來都使用 calibre 原本對 USB 裝置的操作流：

- Mac calibre 選書 → 「發送到裝置」→ 書落進手機的收件資料夾
- 手機把新書放進收件資料夾 → Mac calibre 按「從裝置新增」→ 入庫
- 在 calibre 裝置视图裡刪除手機上的書、退出裝置，全部原生支援

交互模式與 KOReader 的 calibre wireless plugin 一致（它是本協議現存活著、
持續適配新版 calibre 的開源實作，作為行為參考；實作時**只對照行為，
不逐行搬譯 Lua**——KOReader 為 AGPL。協議 ground truth =
`src/calibre/devices/smart_device_app/driver.py`，calibre v7.10.0 為準，
已存進本 repo `docs/protocol-notes.md`）。

環境事實：

- 伺服器端：Mac `/Applications/calibre.app` 7.10.0（需在其偏好中啟用無線裝置存取；
  實施時核對確切開關路徑與 8.x 相容性——用戶可升級 calibre，協議 `serverProtocolVersion: 1` 多年未變）
- 測試機：Xiaomi Mi MIX 2S（polaris, LineageOS 22 / Android 15, API 35），已接 adb
- 書庫：Mac `~/calibre 書庫`

## 2. 範圍（v1）

### 做

- `:wireless` 純 JVM Kotlin 協議引擎（無 Android 依賴）：UDP 廣播探索 + 手動位址、
  TCP 長度假前綴幀、握手/鎖步狀態機、全部 opcode、收件箱檔案表
- 裝置行為：接 `SEND_BOOK`（含二進制流→寫檔）、供 `GET_BOOK_FILE_SEGMENT`、
  接 `DELETE_BOOK`、元數據快取協議（`GET_BOOK_COUNT`/`NOOP count/priKey`/
  `SEND_BOOKLISTS`/collections 最小應答）、`FREE_SPACE`/`TOTAL_SPACE`、
  `SET_CALIBRE_DEVICE_INFO`/`SET_LIBRARY_INFO`、`NOOP ejecting`、可選密碼 sha1
- 裝置書目持久化 = 收件夾內 `metadata.calibre` + `driveinfo.calibre`
  （與真 USB 裝置同格式同檔名，同目錄可混用線材/無線）
- 安全：lpath 路徑穿越防護（照 KOReader `isSafeLpath` 行為重寫）
- `:app`：前台 Service 跑引擎 + Compose UI（連接狀態/開關、UDP 探索或手輸 host:port、
  密碼、SAF 選收件夾、裝置書單、傳輸 log）+ 常駐通知
- 斷線重連（指數退避）、`CALIBRE_BUSY` 處理

### 不做（加回時機）

- 閱讀進度回傳（協議有 `isReadSyncCol`/`_is_read_`，v1 一律不宣告不帶值；
  需要時二期在 init info 拉開關）
- 封面縮圖（`thumbnail` 欄位會收到，忽略；要做再存檔）
- 手機端主動拉庫（協議做不到，方案 A 才有，已否）
- 背景無服務常驻同步（引擎跑在前台 Service，使用者開著才同步——與 KOReader 同模式）

## 3. 技術決策

| 項目 | 決定 |
|------|------|
| 模組 | 2 個：`:wireless`（pure Kotlin JVM）、`:app`（Android） |
| 語言/UI | Kotlin + Compose (Material 3) + Coroutines |
| 協議 IO | `java.net.Socket` + DataInputStream（協議就是原始 TCP，無函式庫） |
| JSON | kotlinx.serialization（幀為 `[opcode, payload]` 陣列） |
| 儲存 | 書目 = 收件夾 `*.calibre` JSON 檔；設定 = DataStore；不用 DB |
| minSdk/targetSdk | 26 / 35，sideload，繁中 UI |
| 抽象縫 | `InboxStore` 介面（list/size/read/write/delete）：JVM=File 實作（測試用），Android=SAF tree URI 實作 |

## 4. 協議規格（源碼實測，v7.10.0）

### 傳輸層

- 探索：向 UDP port `54982/48123/39001/44044/59678` 任一個廣播 `"hello"`
  （本端 bind 8134），回包格式
  `calibre wireless device client (on <host>);<content_server_port>,<tcp_port>`
- 或手動 host:port。TCP 連接後進入幀協議：
  JSON 訊息 = `<十進位ASCII位元組長度><UTF-8 JSON>`，JSON 恆為 `[opcode:int, payload:object]`
  （calibre 讀取演算法：先讀到 `[` 為止得到長度前綴，湊滿 total_len 位元組）
- 檔案二進制**無幀**：`SEND_BOOK` 回 OK 後直讀 `length` 位元組；
  裝置供檔同理先回 `OK{fileLength}` 再 raw 位元組流（可任切 chunk，上限 init 宣告的
  `maxBookContentPacketLen`，KOReader 用 4096）
- 鎖步：calibre 發 request → 裝置必回一條（OK/ERROR）；少數 one-way
  （`SEND_BOOKLISTS` 宣告 wait_for_response=False，裝置**不得**應答，
  但每個 `SEND_BOOK_METADATA` 亦為推式、不應答）

### opcode 表（calibre→裝置 請求；裝置→calibre 應答）

| code | 名稱 | 裝置職責 |
|---|---|---|
| 0 | OK | 通用應答（payload 依請求） |
| 1 | SET_CALIBRE_DEVICE_INFO `{device_store_uuid, device_name, ...}` | 存 driveinfo.calibre，回 OK |
| 2 | SET_CALIBRE_DEVICE_NAME | 同上更新，回 OK |
| 3 | GET_DEVICE_INFORMATION | 回 `OK{device_info:{device_store_uuid, device_name}, version, device_version}`；uuid 首見時生成並永久保存（calibre 靠它記裝置） |
| 4 | TOTAL_SPACE | 回 `OK{total_space_on_device}` |
| 5 | FREE_SPACE | 回 `OK{free_space_on_device}`（用收件夾所在卷統計） |
| 6 | GET_BOOK_COUNT `{canStream, canScan, willUseCachedMetadata, supportsSync, canSupportBookFormatSync}` | 回 `OK{count, willStream:false, willScan:false}`，隨後**主動續發** count 條 `OK{priKey, uuid, lpath, last_modified}`（取自書表） |
| 8 | SEND_BOOK `{lpath, length, metadata, thisBook, totalBooks, ...}` | 回 OK（可帶新 lpath）後進 raw 接收態寫檔；失敗回 `ERROR{message}`（calibre≥4.18 認）；寫入前校驗 lpath，寫入後 `metadata.lpath` 以**校驗過的 lpath** 為準入表 |
| 9 | GET_INITIALIZATION_INFO `{serverProtocolVersion, validExtensions, passwordChallenge, currentLibraryName/UUID, calibre_version, ...}` | 回 OK + init info（見下） |
| 11 | BOOK_DONE | （新 calibre 用；v1 若收到回 OK 即可，實測決定是否涉及） |
| 12 | NOOP `{}` / `{ejecting:true}` / `{count:n}` / `{priKey:i}` | 分別回 OK / 回 OK 後準備斷線 / 設 pending 計數 / 回第 i 本完整書目 |
| 13 | DELETE_BOOK `{lpaths:[...]}` | 先回 OK，再**逐條**回 `OK{uuid}`（條數必等於 lpaths 數）；删除需過 lpath 校驗 |
| 14 | GET_BOOK_FILE_SEGMENT `{lpath, position, ...}` | 回 `OK{fileLength}` + 整檔 raw（v1 忽略 position，與 KOReader 同）；檔不存在回 NOOP |
| 15 | GET_BOOK_METADATA `{index}` | 回 OK + 完整書目 |
| 16 | SEND_BOOKLISTS `{count, collections, willStreamMetadata}` | **不應答**；記 pending=count |
| 16' | SEND_BOOK_METADATA `{index, count, data}` | 不應答；更新書目 data.lpath；最後一本時 save |
| 17 | DISPLAY_MESSAGE `{messageKind}` | kind1=密碼錯（斷線提示）；kind2=要求更新 app；kind3=toast；一律回 OK |
| 18 | CALIBRE_BUSY `{otherDevice}` | 已有別台裝置連著；退避重試 |
| 19 | SET_LIBRARY_INFO | 存 currentLibraryName 等給 UI，回 OK |
| 20 | ERROR | （裝置→calibre 方向）|

### GET_INITIALIZATION_INFO 應答（裝置必帶欄位）

```
versionOK:true, canStreamBooks:true, canStreamMetadata:true,
canReceiveBookBinary:true, canDeleteMultipleBooks:true,
canUseCachedMetadata:true, cacheUsesLpaths:true, canSendOkToSendbook:true,
canAcceptLibraryInfo:true, willAskForUpdateBooks:false,
appName:"CalibreWireless"(自報家門，勿用 CalibreCompanion——會觸發強制升級檢查),
deviceKind:<型號>, deviceName:<顯示名>, ccVersionNumber:<int>,
acceptedExtensions:[epub,fb2,mobi,azw3,txt,pdf,...], extensionPathLengths:{ext:len},
coverHeight:240, maxBookContentPacketLen:4096, useUuidFileNames:false,
passwordHash: sha1_hex(password + passwordChallenge)（無密碼則 ""）
```
（v1 不宣告 `setTempMarkWhenReadInfoSynced`/`isReadSyncCol*`/`willAskForUpdateBooks`）

### 生命週期

连接→9→3→(1)→(2)→19→同步期（6→12*→8→7/16→13…）→ 12{ejecting}→ 斷線。
閒置 30 分鐘 calibre 可自動斷（OPT_AUTODISCONNECT）；裝置端被動即可。
裝置斷線前主動發 `NOOP{}`（KOReader 行為，保留）。

## 5. 架構

```
:wireless/src/main/kotlin/.../
  Frame.kt          // encode/decode <len><json [op,payload]>；純函數
  Opcodes.kt
  InboxStore.kt     // interface: list/size/delete/openRead/openWrite
  DeviceBooks.kt    // metadata.calibre / driveinfo.calibre 書表（kotlinx 序列化）
  Lpath.kt          // isSafeLpath 等價校驗
  Session.kt        // 鎖步狀態機：receive loop + opcode handlers + raw 傳輸態
  Discover.kt       // UDP hello → host:port（JVM 可用）
  WirelessDevice.kt // 外殼: connect(host,port,password,store)->Session; 回調/events Flow
:app/.../
  service/WirelessService.kt   // Foreground service 持 Session + 通知進度
  saf/SafInboxStore.kt         // tree URI → InboxStore
  ui/{MainScreen,SettingsSheet,BooksScreen}.kt
```

`Session` 不碰 Android API；測試用 `FakeCalibre`（測試模組內 ServerSocket 照 §4 指令劇本說話）
跑全流程。依賴方向 `app → wireless`。

## 6. 錯誤處理

| 狀況 | 行為 |
|------|------|
| 密碼錯 | DISPLAY_MESSAGE kind1 → 斷線、UI 提示重輸密碼（保留位址） |
| CALIBRE_BUSY | 提示「calibre 正連線其他裝置」，30s 退避重連 |
| 版本不相容（versionOK 邏輯） | 顯示「請升級本 app/calibre」，停重連 |
| 非法 lpath（穿越/反斜線/無擴展名） | SEND_BOOK/DELETE 回 ERROR（calibre≥4.18 會顯示），不寫不刪 |
| 剩餘空間不足 | SEND_BOOK 前查 `FREE_SPACE`，回 ERROR，不截斷 |
| TCP 斷 | 5s 起指數退避自動重連（探索過的有效位址复用；上限 60s） |
| SAF 權限回收 | 通知「請重新選取資料夾」，Service 待機不崩 |

## 7. 測試

- **JVM 單元（:wireless）**：Frame 編解碼（含半包/粘包/長度前綴跨 read）；Lpath 校驗
  矩陣；DeviceBooks 增刪改存 prune（檔案缺失/損壞/多餘條目）；
  `FakeCalibre` 指令劇本 E2E：完整握手+密碼正確/錯、GET_BOOK_COUNT 兩條路徑
  （cache 命中/miss）、SEND_BOOK 單本/多本/中途 ERROR、GET_BOOK_FILE_SEGMENT 全檔、
  DELETE_BOOK 逐條 uuid 數對、ejecting
- **真機連調（Mac 可跑，無需 Android）**：`:wireless` CLI main() 連真 calibre 7.10：
  探索到→calibre 工具欄出現裝置→GUI 發一本→收件夾見檔→「從裝置新增」入庫一本
  → eject 斷線。此為 M2 驗收，也是對 §4 的活體驗證
- **實機 E2E（adb, M4）**：1) 手機端連接後 Mac 裝置欄出現本裝置；2) GUI 發送 2 本
  （含中文檔名+子目錄 lpath）落 SAF 夾且檔可開；3) SAF 夾放新書→「從手機新增」入庫
  正確 metadata；4) 裝置视图刪除→檔真消失+書表同步；5) 斷 Wi-Fi 再復→自動重連免再握手密碼；
  6) 退出裝置→app 狀態回待機；7) 重啟 app 免再輸 auth（無此物）/密碼、uuid 不變致 calibre 認得原書單
- 驗收 = 三層全綠。§4 與真機有出入時，以驅動源碼為準修 §4 並記錄。

## 8. 里程碑

1. **M0 協議存檔**：driver.py/KOReader 參考源碼摘要 → `docs/protocol-notes.md`（本 spec 附）✅（源碼已核，成文於實施第一 task）
2. **M1 引擎**：Frame+Lpath+DeviceBooks+Session+FakeCalibre 測試全綠
3. **M2 活體驗證**：JVM CLI 連 Mac 真 calibre 走完 §7 第二層清單
4. **M3 Android 外殼**：SafInboxStore+ForegroundService+UI+通知
5. **M4 實機 E2E**：§7 第三層 1–7 全過，交付日常用
