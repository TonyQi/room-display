package com.roomdisplay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

data class RoomState(val id: String, val name: String, val color: String, val icon: String) {
    fun json() = JSONObject().put("id",id).put("name",name).put("color",color).put("icon",icon)
    companion object { fun parse(j: JSONObject) = RoomState(j.getString("id"),j.getString("name"),j.getString("color"),j.getString("icon")) }
}
data class Snapshot(val number: String, val name: String, val states: List<RoomState>, val current: String) {
    fun json() = JSONObject().put("number",number).put("name",name).put("states",JSONArray().apply { states.forEach { put(it.json()) } }).put("current",current)
    fun validate() {
        require(number.isNotBlank() && number.length <= 24) { "房间号须填写且不超过24字" }
        require(name.isNotBlank() && name.length <= 80) { "房间名称须填写且不超过80字" }
        require(states.isNotEmpty() && states.size <= 30) { "请保留1至30个状态" }
        require(states.map { it.id }.distinct().size == states.size && states.map { it.name }.distinct().size == states.size) { "状态名称或编号重复" }
        states.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 16 && it.color.matches(Regex("#[0-9a-fA-F]{6}")) && it.icon.isNotBlank() && it.icon.length <= 8) { "请检查状态名称、图标和颜色（例如 #83DFC2）" } }
        require(states.any { it.id == current }) { "当前状态不存在" }
    }
    companion object {
        fun parse(j: JSONObject) = Snapshot(j.getString("number"), j.getString("name"), j.getJSONArray("states").let { a -> (0 until a.length()).map { RoomState.parse(a.getJSONObject(it)) } }, j.getString("current")).also { it.validate() }
        fun initial() = Snapshot("0306", "第三会议室", listOf(RoomState("free","空闲","#83DFC2","✓"),RoomState("busy","使用中","#FFC18E","◷"),RoomState("closed","暂停使用","#F7A9B1","−")), "free")
    }
}
data class LocalData(val deviceId: String, val revision: Long, val snapshot: Snapshot, val server: String, val token: String, val managed: Boolean, val orientation: String, val lock: Boolean, val ack: JSONObject?, val applied: String) {
    fun json() = JSONObject().put("deviceId",deviceId).put("revision",revision).put("snapshot",snapshot.json()).put("server",server).put("token",token).put("managed",managed).put("orientation",orientation).put("lock",lock).put("ack",ack ?: JSONObject.NULL).put("applied",applied)
}
class RoomStore(context: Context) {
    private val prefs = context.getSharedPreferences("room-display",Context.MODE_PRIVATE)
    private var data: LocalData
    init {
        val raw=prefs.getString("data",null)
        data=if(raw==null) LocalData(UUID.randomUUID().toString(),0,Snapshot.initial(),"","",false,"landscape",false,null,"") else {
            val j=JSONObject(raw)
            LocalData(j.getString("deviceId"),j.getLong("revision"),Snapshot.parse(j.getJSONObject("snapshot")),j.optString("server"),j.optString("token"),j.optBoolean("managed"),j.optString("orientation","landscape"),j.optBoolean("lock"),j.optJSONObject("ack"),j.optString("applied"))
        }
        if(raw==null) write(data)
    }
    @Synchronized fun read() = data.copy(ack=data.ack?.let { JSONObject(it.toString()) })
    @Synchronized private fun write(next: LocalData) {
        check(prefs.edit().putString("data",next.json().toString()).commit()) { "本机保存失败，请检查存储空间" }
        data=next
    }
    @Synchronized fun changeSnapshot(snapshot: Snapshot, expectedRevision: Long) { check(data.revision==expectedRevision) { "数据已更新，请重新操作" }; snapshot.validate(); write(data.copy(snapshot=snapshot,revision=data.revision+1)) }
    @Synchronized fun settings(snapshot: Snapshot, server: String, managed: Boolean, orientation: String, lock: Boolean, expectedRevision: Long) {
        check(data.revision==expectedRevision) { "数据已更新，请重新打开设置" }; snapshot.validate()
        val changed=server!=data.server
        write(data.copy(snapshot=snapshot,revision=data.revision+1,server=server,managed=managed,orientation=orientation,lock=lock,
            token=if(changed) "" else data.token,deviceId=if(changed) UUID.randomUUID().toString() else data.deviceId,
            ack=if(changed) null else data.ack,applied=if(changed) "" else data.applied))
    }
    @Synchronized fun setToken(expectedId: String, expectedServer: String, token: String) { if(data.deviceId==expectedId && data.server==expectedServer) write(data.copy(token=token)) }
    @Synchronized fun newIdentity() { write(data.copy(deviceId=UUID.randomUUID().toString(), token="", ack=null,applied="")) }
    @Synchronized fun handleResponse(sent: LocalData, response: JSONObject): Boolean {
        if(data.deviceId!=sent.deviceId || data.server!=sent.server || !data.managed) return false
        var next=data
        if(sent.ack!=null && data.ack?.optString("id")==sent.ack.optString("id")) next=next.copy(ack=null)
        val cmd=response.optJSONObject("command")
        var applied=false
        if(cmd!=null) {
            val id=cmd.getString("id")
            var result="CONFLICT";var message="本机状态已更新或指令过期"
            if(next.applied==id) { result="APPLIED";message="已保存，重复指令不再执行" }
            else if(cmd.getLong("baseRevision")==next.revision && cmd.getLong("expiresAt")>System.currentTimeMillis()) {
                try {
                    val snapshot=Snapshot.parse(cmd.getJSONObject("snapshot"))
                    next=next.copy(snapshot=snapshot,revision=next.revision+1,applied=id)
                    result="APPLIED";message="设备已持久保存";applied=true
                } catch(e: Exception) { result="FAILED";message=e.message ?: "无效状态数据" }
            }
            next=next.copy(ack=JSONObject().put("id",id).put("result",result).put("message",message))
        }
        write(next)
        return applied
    }
    fun passwordSet() = prefs.contains("passwordHash")
    fun verify(password: String): Boolean {
        val salt=prefs.getString("passwordSalt",null) ?: return false
        val expected=prefs.getString("passwordHash",null) ?: return false
        return MessageDigest.isEqual(Base64.decode(expected,Base64.NO_WRAP), derive(password,Base64.decode(salt,Base64.NO_WRAP)))
    }
    fun setPassword(password: String) {
        require(password.length>=6) { "密码至少6位" }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        check(prefs.edit().putString("passwordSalt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("passwordHash",Base64.encodeToString(derive(password,salt),Base64.NO_WRAP)).commit()) { "密码保存失败" }
    }
    private fun derive(password: String,salt: ByteArray): ByteArray {
        val spec=PBEKeySpec(password.toCharArray(),salt,120000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
