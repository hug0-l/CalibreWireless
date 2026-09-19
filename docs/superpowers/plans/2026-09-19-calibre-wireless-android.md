# CalibreWireless 實施計劃（smart-device 純裝置模擬，spec v2）

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans 或 subagent-driven-development，逐 task 執行。步驟以 checkbox 追蹤。

**Goal:** Android app 向 Mac calibre 模擬「無線裝置」（smart-device JSON-over-TCP 協議），書本往來由 calibre GUI 驅動；app 負責連接/接收/供檔/書表/UI。

**Architecture:** `:wireless` 純 JVM Kotlin 協議引擎（Frame 幀 + Session 鎖步狀態機 + InboxStore 抽象），`:app` Android 外殼（Foreground Service + SAF InboxStore + Compose UI）。測試三層：JVM FakeCalibre 劇本 → CLI 連 Mac 真 calibre → adb 實機。

**Tech Stack:** Kotlin 2.0.21, kotlinx-serialization-json 1.7.3, coroutines 1.9.0, AGP 8.7.3, Compose BOM 2024.12.01, JUnit4, minSdk 26 / target 35, JDK 19(既有)。

**Spec:** `docs/superpowers/specs/2026-09-19-calibre-wireless-android-design.md`（§4 為協議权威；源碼出處 calibre v7.10.0 driver.py + KOReader 行為參考）

## Global Constraints

- 協議 ground truth = calibre `smart_device_app/driver.py`；幀 = `<十進位長度><[opcode,payload] JSON>`；鎖步：每個請求恰回一條（明列 one-way 者**不回應答**）
- 不複製 KOReader/AGPL 源碼，僅對照行為
- JSON 一律 UTF-8；長度前綴 = JSON 位元組數（從 `[` 起算）
- `maxBookContentPacketLen=4096`；`coverHeight=240`；`versionOK=true`；`appName="CalibreWireless"`（不可用 CalibreCompanion——觸發強制升級檢查）
- 裝置書表 = 收件夾 `metadata.calibre` + `driveinfo.calibre`（calibre USBMS 同格式）；`device_store_uuid` 永久穩定
- lpath 必過 `Lpath.safe()` 才許寫/刪
- UI 繁中；sideload debug APK；每 task 一個 commit

---

### Task 1: 倉庫骨架 + Frame 幀層

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `wireless/build.gradle.kts`
- Create: `wireless/src/main/kotlin/dev/hug0/calwireless/Frame.kt`
- Test: `wireless/src/test/kotlin/dev/hug0/calwireless/FrameTest.kt`

**Interfaces:**
- Produces: `data class Frame(opcode:Int, json:String)`；`Frame.encode(opcode:Int, payloadJson:String): ByteArray`；`class FrameReader(input: InputStream) { fun next(): Frame? }`（null=EOF/斷線）

- [ ] `brew install gradle`（若無）；`cd ~/CodeProjects/CalibreWireless && gradle wrapper --gradle-version 8.13`
- [ ] 寫 `settings.gradle.kts`（pluginManagement: google+mavenCentral+gradlePluginPortal；`include("wireless")`，`:app` 於 Task 10 加入）、root `build.gradle.kts`（plugins kotlin jvm `2.0.21` apply false）、`gradle.properties`（`org.gradle.jvmargs=-Xmx2g`）、`wireless/build.gradle.kts`：

```kotlin
plugins { kotlin("jvm") version "2.0.21"; kotlin("plugin.serialization") version "2.0.21" }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testRunner = JUnit — 用 testImplementation("junit:junit:4.13.2") + kotlin("test")
}
```

- [ ] 先寫 `FrameTest.kt`（失败测试）：

```kotlin
class FrameTest {
    @Test fun encodeLengthMatchesBytes() {
        val f = Frame.encode(9, """{"a":1}""")
        val s = String(f, Charsets.UTF_8)
        val len = s.takeWhile { it.isDigit() }.toInt()
        assertEquals(len, s.substring(len.toString().length).toByteArray().size)
        assertTrue(s.substring(len.toString().length).startsWith("[9,"))
    }
    @Test fun decodeRoundTrip() {
        val bytes = Frame.encode(0, "{}")
        val fr = FrameReader(ByteArrayInputStream(bytes))
        assertEquals(Frame(0, "{}"), fr.next())
        assertNull(fr.next())
    }
    @Test fun decodeByteAtATime() { // calibre 半包行為
        val bytes = Frame.encode(17, """{"messageKind": 1}""")
        val fr = FrameReader(object : ByteArrayInputStream(bytes) { override fun available() = 0 })
        // 用單字節 input 包覆再測
        val one = java.io.FilterInputStream(ByteArrayInputStream(bytes))
        val fr2 = FrameReader(object : java.io.InputStream() {
            override fun read() = one.read()
            override fun read(b: ByteArray, o: Int, n: Int): Int {
                if (n == 0) return 0; val v = read(); if (v == -1) return -1; b[o] = v.toByte(); return 1
            }
        })
        val f = fr2.next()!!
        assertEquals(17, f.opcode); assertTrue(f.json.contains("messageKind"))
    }
    @Test fun payloadMayContainBrackets() {
        val bytes = Frame.encode(16, """{"data":{"tags":["a]b","c"]}}""")
        val f = FrameReader(ByteArrayInputStream(bytes)).next()!!
        assertEquals("""{"data":{"tags":["a]b","c"]}}""", f.json)
    }
}
```

- [ ] `./gradlew :wireless:test` → 編譯失敗/FAIL
- [ ] 實作 `Frame.kt`（規格見 Global Constraints；reader 算法鏡像 calibre `_read_string_from_net`：逐 byte 讀到 `[` 得十進位總長，湊滿 `[`起算的 total 位元組；opcode=首逗號前綴、payload=首逗號後至末 `]` 前）：

```kotlin
package dev.hug0.calwireless

import java.io.InputStream
import java.io.InterruptedIOException

data class Frame(val opcode: Int, val json: String) {
    companion object {
        fun encode(opcode: Int, payloadJson: String): ByteArray {
            val json = "[$opcode,${payloadJson.replace("\n", "")}]".toByteArray(Charsets.UTF_8)
            return json.size.toString().toByteArray(Charsets.UTF_8) + json
        }
    }
}

class FrameReader(private val input: InputStream) {
    /** null = stream ended (socket closed). */
    fun next(): Frame? {
        val prefix = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            val c = b.toChar()
            if (c == '[') break
            if (!c.isDigit()) continue // 容忍前綴前的雜字節（calibre 同樣 2-byte 掃描）
            prefix.append(c)
        }
        val total = prefix.toString().toIntOrNull() ?: return null
        val buf = ByteArray(total)
        buf[0] = '['.code.toByte()
        var pos = 1
        while (pos < total) {
            val n = input.read(buf, pos, total - pos)
            if (n <= 0) return null
            pos += n
        }
        val json = String(buf, Charsets.UTF_8)
        val comma = json.indexOf(',')
        val opcode = json.substring(1, comma).trim().toInt()
        return Frame(opcode, json.substring(comma + 1, json.length - 1))
    }
}
```

- [ ] `./gradlew :wireless:test --tests "*FrameTest*"` 全綠 → commit `feat(wireless): frame codec + reader`

### Task 2: Op 常量 + Lpath 校驗

**Files:** Create `wireless/.../Opcodes.kt`, `wireless/.../Lpath.kt`；Test `LpathTest.kt`

**Interfaces:** Produces `object Op { const val OK=0; SET_CALIBRE_DEVICE_INFO=1; SET_CALIBRE_DEVICE_NAME=2; GET_DEVICE_INFORMATION=3; TOTAL_SPACE=4; FREE_SPACE=5; GET_BOOK_COUNT=6; SEND_BOOKLISTS=7; SEND_BOOK=8; GET_INITIALIZATION_INFO=9; BOOK_DONE=11; NOOP=12; DELETE_BOOK=13; GET_BOOK_FILE_SEGMENT=14; GET_BOOK_METADATA=15; SEND_BOOK_METADATA=16; DISPLAY_MESSAGE=17; CALIBRE_BUSY=18; SET_LIBRARY_INFO=19; ERROR=20 }`；`object Lpath { fun safe(path: String, extensions: Set<String>? = null): Boolean }`

- [ ] 測試先行（鏡像 KOReader `isSafeLpath` 案例）：`"../x.epub"`,`"/abs.epub"`,`"a\\b.epub"`,`"C:\\x.epub"`,`"a/\u0000.epub"`,`""`,`"a//b.epub"`,`"noext"`,`"x.exe"`(帶 extensions=epub 集) 全 false；`"Author/Book.epub"`,`"Book.epub"`,`"a b/中文字.epub"` true
- [ ] FAIL → 實作 → PASS → commit `feat(wireless): opcodes + lpath guard`

### Task 3: InboxStore 介面 + FileInboxStore

**Files:** Create `wireless/.../InboxStore.kt`；Test `FileInboxStoreTest.kt`

**Interfaces:** Produces:

```kotlin
interface InboxStore {
    fun list(): List<String>                      // 遞迴，'/' 分隔相對路徑
    fun size(path: String): Long?
    fun delete(path: String): Boolean
    fun read(path: String): java.io.InputStream?
    fun write(path: String): java.io.OutputStream? // 自動建父目錄、truncate；null=失敗
    fun readText(path: String): String?
    fun writeText(path: String, text: String): Boolean
    fun usableBytes(): Long
    fun totalBytes(): Long
}
class FileInboxStore(private val root: java.io.File) : InboxStore
```

- [ ] 測試（`@TempDir` 風格 junit TemporaryFolder）：write 嵌套 `a/b/x.epub`→read 內容一致、list 含相對路徑、size、delete 連檔、readText/writeText、usable<=total 且 >0
- [ ] FAIL → FileInboxStore 實作（`root.walkTopDown()` 減目錄、相對 = `file.absolutePath.removePrefix(root.absolutePath).trimStart('/').replace('\\','/')`；容量 `root.usableSpace/root.totalSpace`）→ PASS → commit

### Task 4: DeviceBooks 書表

**Files:** Create `wireless/.../DeviceBooks.kt`；Test `DeviceBooksTest.kt`

**Interfaces:** Produces（書目 = `JsonObject` 直通存，僅 slim 過濾）:

```kotlin
class DeviceBooks(private val store: InboxStore) {
    val books = ArrayList<kotlinx.serialization.json.JsonObject>()
    fun load()                       // metadata.calibre(JSON array) + prune；缺檔=空表
    fun save()                       // writeText pretty；存檔失敗僅 log
    fun count(): Int
    fun upsert(meta: JsonObject, lpath: String)   // 以「校驗過的 lpath」為準覆寫 meta.lpath；slim 後同 lpath 替換或新增
    fun update(meta: JsonObject)                  // SEND_BOOK_METADATA 路徑：按 meta.lpath 替換/新增
    fun remove(lpath: String): String?            // 回 uuid
    fun uuidOf(lpath: String): String             // 查無回 "none"
    fun idFrame(index1Based: Int): String         // {"priKey":i,"uuid":..,"lpath":..,"last_modified":..}
    fun frame(index1Based: Int): String           // 完整 slim 書目
    fun deviceUuid(): String                      // driveinfo.calibre 的 device_store_uuid；無則 random UUID 並立刻存檔（永久穩定）
    fun saveDriveInfo(o: JsonObject)              // 保留既有 device_name 語義照 spec §4
    companion object { const val META_FILE = "metadata.calibre"; const val DRIVE_FILE = "driveinfo.calibre" }
}
```

USED keys（slim 白名單）：`uuid,lpath,last_modified,size,title,authors,author_sort,tags,series,series_index`

- [ ] 測試：空載入、upsert→save→重載 roundtrip、同 lpath 二次 upsert 不重複、remove 回 uuid、prune（表有條目但檔不存在→清掉；用 FileInboxStore + TemporaryFolder）、deviceUuid 重載後不變、idFrame 欄位
- [ ] FAIL → 實作 → PASS → commit

### Task 5: Session —— 握手/空間/庫資訊/eject/busy

**Files:** Create `wireless/.../Session.kt`, `wireless/.../Events.kt`, `wireless/.../DeviceConfig.kt`；Test: `wireless/src/test/.../FakeCalibre.kt`(測試助手), `SessionHandshakeTest.kt`

**Interfaces:** Produces:

```kotlin
data class DeviceConfig(
    val appName: String = "CalibreWireless", val appVersion: String = "1",
    val deviceKind: String, val deviceName: String,
    val maxPacketLen: Int = 4096, val coverHeight: Int = 240,
    val extensions: List<String> = listOf("epub","mobi","azw3","azw","fb2","pdf","txt","rtf","html","doc","docx","cbz","cbr","djvu","djv","lit","lrf","pdb"),
)
sealed class WirelessEvent {
    object Connecting : WirelessEvent()
    data class Connected(val libraryName: String?) : WirelessEvent()
    data class BookReceived(val lpath: String, val size: Long) : WirelessEvent()
    data class BookServed(val lpath: String) : WirelessEvent()
    data class BookDeleted(val lpath: String) : WirelessEvent()
    object PasswordRejected : WirelessEvent()
    data class Busy(val otherDevice: String) : WirelessEvent()
    object Ejected : WirelessEvent()
    data class Disconnected(val cause: String) : WirelessEvent()
    data class Log(val message: String) : WirelessEvent()
}
class Session(
    private val socket: java.net.Socket, private val store: InboxStore,
    private val config: DeviceConfig, private val password: String?,
    private val emit: (WirelessEvent) -> Unit,
) { fun run() /* 阻塞至 ejecting 或 socket 斷 */ }
```

- [ ] `FakeCalibre.kt`（測試端對話鏡子）：

```kotlin
class FakeCalibre(store: InboxStore, config: DeviceConfig, password: String? = null) : AutoCloseable {
    private val pair = java.io.PipedSocketStub() // 實作用兩端本地 socket：ServerSocket(0)+Socket
    // 最終形態：val server=ServerSocket(0); session 線程用 Socket("localhost",port) 連入
    // fun call(op:Int, payload:String): Frame   — 送出後取 Session 的應答
    // fun send(op:Int, payload:String)          — one-way（不等應答）
    // fun sendRaw(bytes: ByteArray); fun readRaw(n:Int): ByteArray
    // fun readByte(): Int — 原始流用
    // close(): 關 socket/session 線程 join
}
```

（寫法：`FakeCalibre` 內部起 `Session` 於 daemon thread，本體持 server socket 的 client InputStream/OutputStream + FrameReader。）

- [ ] 握手測試（先紅）：
  1. `call(GET_INITIALIZATION_INFO, {"serverProtocolVersion":1,"passwordChallenge":"CHAL","currentLibraryName":"測試庫"})` → 應答 OK；解析 payload：`versionOK==true, canStreamBooks==true, canSendOkToSendbook==true, canAcceptLibraryInfo==true, canUseCachedMetadata==true, cacheUsesLpaths==true, canReceiveBookBinary==true, canDeleteMultipleBooks==true, canStreamMetadata==true, passwordHash==sha1hex("pw"+"CHAL")（帶密碼）或 ""（不帶）, maxBookContentPacketLen==4096, acceptedExtensions 含 "epub", extensionPathLengths["epub"]==4, ccVersionNumber 存在`
  2. `call(GET_DEVICE_INFORMATION,"{}")` → `device_info.device_store_uuid` 36 字元 uuid 格式；同 FakeCalibre 重開第二輪後 uuid 不變（driveinfo 已落檔）
  3. `call(FREE_SPACE,"{}")`→ `free_space_on_device>0`；`call(TOTAL_SPACE)` 同
  4. `call(SET_CALIBRE_DEVICE_INFO, {device_store_uuid:...,device_name:"CalibreWireless (Mi MIX 2S)"})`→OK；driveinfo.calibre 落檔
  5. `call(SET_LIBRARY_INFO, {"current_library_name":"庫名"})`→OK 且事件流出現 `Connected("庫名")`
  6. `call(NOOP,"{}")`→OK keep-alive；`call(NOOP,"{ejecting:true}")`→OK 後 `Session.run` 返回、`Ejected` 事件
  7. 首訊息 `CALIBRE_BUSY {"otherDevice":"Kobo"`→ `Busy` 事件、run 返回
  8. `send(DISPLAY_MESSAGE,{messageKind:1})`→ `PasswordRejected`
- [ ] 實作 Session（when-dispatch 照 spec §4 表；one-way opcode（`SEND_BOOKLISTS`、`SEND_BOOK_METADATA`、`NOOP{count}`）**不得**應答；unknown→OK `{}`）。sha1Hex 用 `MessageDigest`。其餘 opcode handler 此 task 先回 OK 佔位（Task 6-8 補）→ 測試綠 → commit
- 骨架要點：`run()` = `books.load(); while(!ejecting){ val f=reader.next()?:break; try{handle(f)}catch(e:Exception){emit(Disconnected(...));break} }`

### Task 6: 書單同步（GET_BOOK_COUNT / NOOP priKey / SEND_BOOKLISTS / SEND_BOOK_METADATA / GET_BOOK_METADATA）

**Files:** Modify `Session.kt`；Test `SessionBooklistTest.kt`

- [ ] 測試：預置兩本檔 `a/A.epub`(uuid u1,lastmod L1) `b/B.epub`(u2) 且 metadata.calibre 已由 upsert 寫好（直接用 DeviceBooks 建）
  1. `call(GET_BOOK_COUNT, {"willUseCachedMetadata":true})` → 依序收 OK{count:2,willStream:true,willScan:true}、OK{priKey:1,uuid:"u1",lpath:"a/A.epub",last_modified:L1}、OK{priKey:2,...}
  2. cache 迴圈：`send(NOOP,{count:1})` 不應答；`call(NOOP,{priKey:1})` → OK + 完整 slim（含 title/authors）；多余欄位（thumbnail）確認被 slim 掉
  3. `send(SEND_BOOKLISTS,{count:1,collections:{}})` 不應答；`send(SEND_BOOK_METADATA,{index:0,count:1,data:{...u2 lpath 新 title 新欄位 x}})` 不應答；此時書表已更新且 **落檔**（重開 FakeCalibre 可見）
  4. `call(GET_BOOK_METADATA,{index:1})` → OK+該書
- [ ] 實作 → 全綠 → commit `feat(wireless): booklist/metadata-cache flows`

### Task 7: SEND_BOOK（calibre→手機收書）

**Files:** Modify `Session.kt`；Test `SessionSendBookTest.kt`

- [ ] 測試：
  1. 正常：`call(SEND_BOOK, {lpath:"作者/書名.epub", length:5, metadata:{uuid:"u9",lpath:"作者/書名.epub",title:"書名",size:5,last_modified:"2026-01-01"}})` 收 OK → `sendRaw("12345")` → 檔存在內容對、書表入列、`BookReceived` 事件
  2. 惡意 lpath `../evil.epub` → 收 **ERROR**（此時 calibre 不會再發 raw——照 driver `_put_file`）→ 無檔產生
  3. 空間不足：FakeCalibre store 注入假 usableBytes<length → ERROR 且不進 raw 態
  4. 多本連續：SEND_BOOK×2 間夾一個 FREE_SPACE 請求仍對位（驗證無殘留未讀 raw）
  5. `useUuidFileNames=false` 路徑：metadata.lpath 與請求 lpath 不同時，以**請求 lpath** 寫檔與入表（pin）
- [ ] 實作：`Lpath.safe(lpath, exts)` → 容量（`length+131072` 門檻，鏡像 KOReader）→ OK → 讀 `length` 原始位元組進 `store.write(lpath)` → `books.upsert(meta, lpath); books.save()` → 事件。readBinary(n) 迴圈讀滿 → 全綠 → commit

### Task 8: GET_BOOK_FILE_SEGMENT（手機→calibre 供書）+ DELETE_BOOK

**Files:** Modify `Session.kt`；Test `SessionServeDeleteTest.kt`

- [ ] 測試：
  1. 供檔：預置 `in/x.epub` bytes(10000)；`call(GET_BOOK_FILE_SEGMENT,{lpath:"in/x.epub",position:0})` → OK{fileLength:10000} + 讀 server 端 raw 10000 位元組比對（注意：Session 用宣告的 4096 包長切塊，但 TCP 流連續，讀滿 total 即可）→ `BookServed`
  2. 檔不存在/非法 lpath → `call` 收 **NOOP**（不拋、不斷線，後續請求正常）
  3. 刪除：兩本在表 → `call(DELETE_BOOK,{lpaths:["a/A.epub","nope.epub"]})` → 第一條 OK，再恰 **2 條** OK{uuid}（第二條 uuid="none"）；檔真刪、表移除、save 落檔
  4. DELETE_BOOK 惡意路徑 `../x` → 該條不刪檔仍回 OK{uuid:none}（鏡像 KOReader 保守行為：回 envelope 數必對）
- [ ] 實作 → commit

### Task 9: Discover + WirelessDevice 重連外殼 + CLI

**Files:** Create `wireless/.../Discover.kt`, `wireless/.../WirelessDevice.kt`, `wireless/.../Cli.kt`；Test `DiscoverParseTest.kt`

**Interfaces:** `data class CalibreServer(host:String, tcpPort:Int, opdsPort:Int?)`；`Discover.hello(timeoutMs:Int=3000): CalibreServer?`、`Discover.parseReply(dgram:String, fromHost:String): CalibreServer?`；`class WirelessDevice(store, config, password, address: ()->Pair<String,Int>, emit) { fun runLoop(isStopped: ()->Boolean) }`（退避 5s→60s×2；eject/正常斷線重置 5s）

- [ ] parse 測試：`"calibre wireless device client (on hugo-mac);8080,8135"` → host/tcp=8135/opds=8080；content port 空字串 `");,8135"` → tcp=8135, opds=null
- [ ] UDP 實作（發 "hello" 輪詢 `BROADCAST_PORTS={54982,48123,39001,44044,59678}`，setSoTimeout 各 1s）
- [ ] `Cli.kt`：`--host/--port/--password/--inbox DIR/--name`；缺 host:port 先 Discover；事件 println；此即 M2 驗收工具
- [ ] **M2 活體驗證（人工，Mac 開著新升級的 calibre）**：calibre → 偏好設定 → 搜尋「无线」啟用 Android 無線裝置存取（勾「啟動時允許連線」、固定 port 8135、可設密碼）→ 終端 `./gradlew :wireless:installDist && ./wireless/build/install/wireless/wireless --inbox /tmp/cal-test` → 對照清單：①工具欄出現裝置圖標（名稱含 CalibreWireless）②選 2 本「發送到裝置」→ /tmp/cal-test 見檔+metadata.calibre ③放一本新書進 /tmp/cal-test → 「從手機新增」入庫且 metadata 對 ④裝置视图刪一本→檔消失 ⑤calibre 退出裝置→CLI 印 Ejected 後自動重連 ⑥帶錯密碼→PasswordRejected。**每項結果記錄進 commit message**；與 spec §4 有出入→先改 spec 併碼
- [ ] commit `feat(wireless): discovery + reconnect + CLI (M2 verified)`

### Task 10: Android 工具鏈 + :app 骨架

- [ ] `brew install --cask android-commandlinetools`（失敗則 `android-studio`）；`sdkmanager "platforms;android-35" "build-tools;35.0.0"` 併 `--licenses` 接受；`sdk.dir` 寫 `local.properties`
- [ ] `:app` Gradle（AGP 8.7.3, compose, minSdk 26）+ Manifest：INTERNET/ACCESS_NETWORK_STATE/FOREGROUND_SERVICE/FOREGROUND_SERVICE_DATA_SYNC/POST_NOTIFICATIONS；MainActivity exported
- [ ] `./gradlew :app:assembleDebug` 綠 → commit

### Task 11: SafInboxStore

**Files:** Create `app/.../saf/SafInboxStore.kt`

**Interfaces:** `class SafInboxStore(context: Context, treeUri: Uri) : InboxStore` —— 遞迴 `DocumentsContract.buildChildDocumentsUri` 列表；`write` 逐级 `findOrCreateDir`（`createDocument("vnd.android.document/directory", name)`）→ `createDocument("application/octet-stream", fileName)` → `contentResolver.openOutputStream(uri,"w")`；`readText/writeText` 同路徑；容量 `StatFs(Environment.getExternalStorageDirectory() + "/" + treeUri.pathSegments.drop(1).joinToString("/"))`（"primary" 開頭）

- [ ] 已知坑寫碼時處理：SAF 部分 provider 會幫 octet-stream 檔改名（回傳 uri 為準記 actual name，list 以 actual 相對路徑比對，書表 lpath 仍用請求值）；`.calibre` 檔走 writeText 同 createDocument 路徑
- [ ] 編譯 + `assembleDebug` 綠；行為驗證併入 Task 14 實機清單 → commit

### Task 12: WirelessService + 共享狀態 + 通知

**Files:** Create `app/.../DeviceState.kt`, `app/.../WirelessService.kt`

- `object DeviceState { val status=MutableStateFlow("idle"); val logs=MutableStateFlow<List<String>>(emptyList()); val running=MutableStateFlow(false) }`；事件 append logs（上限 200）+ status 映射
- Service：START 帶 extras（host?/port?/password?/treeUri?）；foreground type dataSync + 通知（狀態文字）；`scope.launch(Dispatchers.IO){ WirelessDevice(...).runLoop{stopped} }`；STOP→eject 語義=直接停（下次 calibre 端自然斷線）→ assembleDebug → commit

### Task 13: Compose UI

**Files:** Create `app/.../ui/MainActivity.kt`, `ui/MainScreen.kt`

- 單頁：頂部狀態卡（idle/connecting/connected 庫名/最近事件）+ 主開關；「設定」sheet：自動探索/手輸 IP:port 切換、host、port、密碼、裝置名（預設 Build.MODEL，可改）、「選取收件夾」（`OpenDocumentTree`，顯示持久化 treeUri）；下方事件 log 列表（auto-scroll）；啟動前 POST_NOTIFICATIONS 請求；設定存 DataStore（`app/build.gradle.kts` 加 `androidx.datastore:datastore-preferences:1.1.1`）
- `assembleDebug` + 起真機 smoke（裝得上、開得了、選資料夾持久化）→ commit

### Task 14: 實機 E2E + 收尾

- [ ] `adb install -r` 於 MIX 2S；跑 spec §7 實機清單 1–7（含拔網重連、重啟 app uuid 不變致 calibre 認書單、密碼錯）
- [ ] 修到全綠；README.md（calibre 端設定 + 使用流）；commit `docs: usage`
- [ ] 交付：app 開著→Mac calibre 操作

## Self-Review 結論（撰寫時）

- Spec §2「做」逐條有對應 task：握手/六流/eject/密碼 ✅ T5-8；探索/退避 ✅ T9；SAF 書表 ✅ T4+T11；Service/UI ✅ T12-13；錯誤表 7 行 → T5-8/T9/T11/T12 各就位
- 型別跨 task 一致：Frame/Op/InboxStore/DeviceBooks/WirelessEvent/DeviceConfig 自 T1-5 定義後引用未改名
- 無佔位：T5 的「handler 佔位回 OK」是顯式過渡（T6-8 逐個充實），FakeCalibre 註解塊為寫法指引而非代碼缺漏

## M2 活體驗證記錄（2026-09-19，calibre 9.15.0 @ Mac）

- 探索：UDP hello → tcp 9091（fixed port）✓
- 密碼：空 hash 被 DISPLAY_MESSAGE kind1 拒 → PasswordRejected ✓；正確密碼過 ✓
- SET_LIBRARY_INFO 鍵為 `libraryName`（7.10/9.15 皆然，非 current_library_name）→ 已修正代碼+測試
- 下行：2 本 epub 共 30MB，size 精確、metadata.calibre 中文 title/authors/tags ✓
- 退出裝置 → 5s 自動重連 → 再 Connected ✓
- 上行：手動丟 PDF →（重開裝置會話觸發 GET_BOOK_COUNT）harvest 入列 → 裝置視圖可見
- 入庫：`GET_BOOK_FILE_SEGMENT` → 庫內 `Xing Ye Biao Zhun - Cai Zhi Gang.pdf` sha1 == 源檔 ✓（用戶於新增對話框改標題作者=行業標準/蔡智剛）
- 新發現 opcode：GET_COLLECTIONS=21/UPDATE_COLLECTIONS=22（9.15 定義未使用）→ 防禦性應答已加
