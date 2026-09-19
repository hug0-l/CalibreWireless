package dev.hug0.calwireless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionHandshakeTest {
    private fun store(): InboxStore {
        val d = createTempDir(); d.deleteOnExit()
        return FileInboxStore(d)
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun FakeCalibre.initHandshake(payload: String = """{"serverProtocolVersion":1,"passwordChallenge":"CHAL","currentLibraryName":"測試庫","calibre_version":[7,10,0]}""") =
        call(Op.GET_INITIALIZATION_INFO, payload)

    @Test fun initInfoWithoutPassword() {
        FakeCalibre(store()).use { fc ->
            fc.start()
            val rep = fc.initHandshake()
            assertEquals(Op.OK, rep.opcode)
            val o = Json.parseToJsonElement(rep.json).jsonObject
            assertTrue(o["versionOK"]!!.jsonPrimitive.content.toBoolean())
            assertTrue(o["canStreamBooks"]!!.jsonPrimitive.content.toBoolean())
            assertTrue(o["canSendOkToSendbook"]!!.jsonPrimitive.content.toBoolean())
            assertTrue(o["canAcceptLibraryInfo"]!!.jsonPrimitive.content.toBoolean())
            assertEquals("", o["passwordHash"]!!.jsonPrimitive.content)
            assertEquals(65536, o["maxBookContentPacketLen"]!!.jsonPrimitive.content.toInt())
            assertEquals(4, o["acceptedExtensions"]!!.jsonArray.first().jsonPrimitive.content.length)
            assertEquals(4, o["extensionPathLengths"]!!.jsonObject["epub"]!!.jsonPrimitive.content.toInt())
            assertNotNull(o["ccVersionNumber"])
            assertEquals("CalibreWireless", o["appName"]!!.jsonPrimitive.content)
        }
    }

    @Test fun passwordHashMatchesChallenge() {
        FakeCalibre(store(), password = "pw").use { fc ->
            fc.start()
            val o = Json.parseToJsonElement(fc.initHandshake().json).jsonObject
            assertEquals(sha1("pwCHAL"), o["passwordHash"]!!.jsonPrimitive.content)
        }
    }

    @Test fun deviceInfoUuidStable() {
        val s = store()
        lateinit var uuid: String
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.initHandshake()
            val o = Json.parseToJsonElement(fc.call(Op.GET_DEVICE_INFORMATION).json).jsonObject
            uuid = o["device_info"]!!.jsonObject["device_store_uuid"]!!.jsonPrimitive.content
            assertEquals(36, uuid.length)
        }
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.initHandshake()
            val o = Json.parseToJsonElement(fc.call(Op.GET_DEVICE_INFORMATION).json).jsonObject
            assertEquals(uuid, o["device_info"]!!.jsonObject["device_store_uuid"]!!.jsonPrimitive.content)
        }
    }

    @Test fun spaceLibraryInfoDeviceName() {
        val s = store()
        FakeCalibre(s).use { fc ->
            fc.start()
            fc.initHandshake()
            val free = Json.parseToJsonElement(fc.call(Op.FREE_SPACE).json).jsonObject
            assertTrue(free["free_space_on_device"]!!.jsonPrimitive.content.toLong() > 0)
            val total = Json.parseToJsonElement(fc.call(Op.TOTAL_SPACE).json).jsonObject
            assertTrue(total["total_space_on_device"]!!.jsonPrimitive.content.toLong() > 0)
            assertEquals(Op.OK, fc.call(Op.SET_CALIBRE_DEVICE_INFO,
                """{"device_store_uuid":"abc","device_name":"CalibreWireless (Test)"}""").opcode)
            assertEquals(Op.OK, fc.call(Op.SET_LIBRARY_INFO, """{"libraryName":"我的庫"}""").opcode)
            fc.call(Op.NOOP, "{}") // sync point: OK 回來後事件已入列
            val ev = fc.events.filterIsInstance<WirelessEvent.Connected>().firstOrNull()
            assertEquals("我的庫", ev?.libraryName)
            // driveinfo 落檔
            assertNotNull(s.readText(DeviceBooks.DRIVE_FILE))
        }
    }

    @Test fun fieldMetadataEmitsColumns() {
        FakeCalibre(store()).use { fc ->
            fc.start()
            fc.initHandshake()
            fc.call(Op.SET_LIBRARY_INFO, """{"libraryName":"L","fieldMetadata":{"#read":{"datatype":"bool"},"#fin":{"datatype":"datetime"},"tags":{"datatype":"text"},"#n":{"datatype":"number"}}}""")
            val ev = fc.events.filterIsInstance<WirelessEvent.LibraryColumns>().first()
            assertEquals(listOf("#read"), ev.boolCols)
            assertEquals(listOf("#fin"), ev.dateCols)
        }
    }

    @Test fun keepaliveThenEjectEndsSession() {
        FakeCalibre(store()).use { fc ->
            fc.start()
            fc.initHandshake()
            assertEquals(Op.OK, fc.call(Op.NOOP, "{}").opcode)
            assertEquals(Op.OK, fc.call(Op.NOOP, """{"ejecting":true}""").opcode)
            assertEquals(WirelessEvent.Ejected, fc.nextEvent())
        }
    }

    @Test fun busyAsFirstMessage() {
        FakeCalibre(store()).use { fc ->
            fc.start()
            fc.send(Op.CALIBRE_BUSY, """{"otherDevice":"Kobo"}""")
            assertTrue(fc.nextEvent(3000) is WirelessEvent.Busy)
        }
    }

    @Test fun passwordErrorDisplaysEvent() {
        FakeCalibre(store(), password = "right").use { fc ->
            fc.start()
            fc.initHandshake()
            // 模擬 calibre 發現密碼錯：推 DISPLAY_MESSAGE kind1 後斷線
            fc.call(Op.DISPLAY_MESSAGE, """{"messageKind":1}""")
            assertTrue(fc.events.filterIsInstance<WirelessEvent.PasswordRejected>().isNotEmpty())
        }
    }

    @Test fun customFormatOrderAndPacketPassThrough() {
        val cfg = DeviceConfig(
            deviceKind = "EInk", deviceName = "Reader",
            maxPacketLen = 32768, extensions = listOf("azw3", "epub", "pdf"),
        )
        FakeCalibre(store(), config = cfg).use { fc ->
            fc.start()
            val o = Json.parseToJsonElement(fc.initHandshake().json).jsonObject
            assertEquals("azw3", o["acceptedExtensions"]!!.jsonArray.first().jsonPrimitive.content)
            assertEquals(4, o["extensionPathLengths"]!!.jsonObject["azw3"]!!.jsonPrimitive.content.toInt())
            assertEquals(32768, o["maxBookContentPacketLen"]!!.jsonPrimitive.content.toInt())
        }
    }

    @Test fun readSyncColsDeclaredWhenConfigured() {
        val cfg = DeviceConfig(deviceKind = "K", deviceName = "D", readSyncCol = "read", readDateSyncCol = "read_date")
        FakeCalibre(store(), config = cfg).use { fc ->
            fc.start()
            val o = Json.parseToJsonElement(fc.initHandshake().json).jsonObject
            assertEquals("#read", o["isReadSyncCol"]!!.jsonPrimitive.content)
            assertEquals("#read_date", o["isReadDateSyncCol"]!!.jsonPrimitive.content)
        }
        FakeCalibre(store()).use { fc ->
            fc.start()
            val o = Json.parseToJsonElement(fc.initHandshake().json).jsonObject
            assertTrue("isReadSyncCol" !in o)
        }
    }

    @Test fun unknownOpcodeDoesNotDeadlock() {
        FakeCalibre(store()).use { fc ->
            fc.start()
            fc.initHandshake()
            assertEquals(Op.OK, fc.call(Op.BOOK_DONE, "{}").opcode)
        }
    }
}
