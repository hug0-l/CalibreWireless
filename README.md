# CalibreWireless

把手機變成 calibre 的「無線裝置」（smart-device 協議，Calibre Companion / KOReader 同款通道）。
書的往來在 Mac 的 calibre GUI 操作；手機這端只需要開著。

## 需求

- Mac calibre ≥ 7.10（實測 9.15.0）。calibre → 偏好設定 → 搜尋「裝置」→ 找到
  「Android 無線裝置 / Smart device」驅動設定：勾 **啟動時允許連線**、
  （建議）固定連接埠、可設密碼
- 手機與 Mac 同一區域網路

## 使用

1. 安裝 `app-debug.apk`（`./gradlew :app:assembleDebug` 產出）
2. app 內：選收件夾（如 `Books`）→ 設定（自動探索即可；有密碼填密碼）
3. 開主開關 → 面板綠燈 = calibre 已把手機當裝置
4. 收書：calibre 選書 → 發送到裝置 → 落進收件夾
5. 交書：把檔丟進收件夾 → calibre **退出裝置** → 等 10 秒自動重連 → 裝置視圖見新書 → 拖回書庫（或「將書本新增至書庫」）

## 開發

- `:wireless` 純 JVM 協議引擎（測試全在此層：FakeCalibre 劇本 48 測）
- `:app` Android 外殼（SAF 收件夾 + 前台 Service + Compose 面板）
- 除錯 CLI：`./gradlew :wireless:installDist && ./wireless/build/install/wireless/bin/wireless --inbox /tmp/x --password …`
- 文件：`docs/superpowers/specs/`（協議規格 §4 為源碼實測）、`docs/superpowers/plans/`（實施計劃 + M2 實測記錄）

## 協議速記（源碼實測 calibre v7.10/9.15 driver.py）

TCP JSON 幀 `<十進位長度>[opcode, {…}]`；calibre 單向鎖步驅動；
SET_LIBRARY_INFO 用 `libraryName`；探索 = UDP "hello" → {54982,48123,39001,44044,59678}；
密碼 = `sha1(password + challenge)`；one-way 訊息（SEND_BOOKLISTS / SEND_BOOK_METADATA / NOOP{count}）不得應答。
