package com.roomdisplay.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var store: RoomStore
    private val handler=Handler(Looper.getMainLooper())
    private val network=Executors.newSingleThreadScheduledExecutor()
    private var connection="单机运行"
    private var lastError=""
    private var lastSuccessful=0L
    private var destroyed=false
    private var registering=false
    private var connected=false
    private var configDialog: AlertDialog?=null
    private var authDialog: AlertDialog?=null
    private var inactiveTimer: Runnable?=null
    private val bg=Color.rgb(10,17,27)
    private val panel=Color.rgb(17,29,43)
    private val text=Color.rgb(232,240,248)
    private val muted=Color.rgb(164,181,200)
    private val accent=Color.rgb(131,223,194)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun color(s:String)=try { Color.parseColor(s) } catch(_: Exception) { accent }
    private fun drawable(fill:Int,stroke:Int=Color.rgb(41,57,75),radius:Float=16f)=GradientDrawable().apply { setColor(fill);cornerRadius=dp(radius.toInt()).toFloat();setStroke(dp(1),stroke) }
    private fun column(pad:Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(pad),dp(pad),dp(pad),dp(pad)) }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun label(value:String,size:Float=16f,tint:Int=text,bold:Boolean=false)=TextView(this).apply { this.text=value;textSize=size;setTextColor(tint);if(bold)setTypeface(null,Typeface.BOLD) }
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(this).apply {
        this.text=value;isAllCaps=false;setTextColor(if(primary)bg else this@MainActivity.text);textSize=16f;minHeight=dp(52);setPadding(dp(16),dp(8),dp(16),dp(8));background=drawable(if(primary)accent else Color.rgb(27,45,61));setOnClickListener { action() }
    }
    private fun space(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun LinearLayout.add(v:View,weight:Float=0f) { addView(v,if(weight>0) LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,weight) else (v.layoutParams ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))) }
    private fun input(value:String="",hint:String="",password:Boolean=false,max:Int=100)=EditText(this).apply {
        setText(value);this.hint=hint;setTextColor(this@MainActivity.text);setHintTextColor(muted);textSize=16f;setPadding(dp(12),dp(12),dp(12),dp(12));background=drawable(bg);isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(max));if(password)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private fun LinearLayout.field(title:String,value:String="",password:Boolean=false,max:Int=100):EditText { add(label(title,14f,muted));add(space(6));val e=input(value,title,password,max);add(e);add(space(16));return e }
    private fun message(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun protect(action:()->Unit) { try { action() } catch(e: Exception) { message(e.message ?: "操作失败") } }
    private fun dialog(title:String,body:View,cancelable:Boolean=true):AlertDialog {
        val scroll=ScrollView(this).apply { isFillViewport=false;addView(body) }
        val d=AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("关闭",null).create()
        d.setCancelable(cancelable);d.setOnShowListener { d.window?.setBackgroundDrawable(drawable(panel));d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent);d.window?.setLayout(minOf(resources.displayMetrics.widthPixels-dp(32),dp(680)),WindowManager.LayoutParams.WRAP_CONTENT) }
        d.show();return d
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store=RoomStore(this)
        applyOrientation();fullscreen();render()
        network.scheduleWithFixedDelay({ sync() },0,2,TimeUnit.SECONDS)
        if(!store.passwordSet()) handler.post { passwordSetup(false) }
    }
    private fun fullscreen() { window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus);if(hasFocus)fullscreen() }
    override fun onPostResume() { super.onPostResume();if(!destroyed && store.read().lock) applyKiosk(true,false) }
    override fun onConfigurationChanged(newConfig:Configuration) { super.onConfigurationChanged(newConfig);render() }
    private fun applyOrientation() { requestedOrientation=if(store.read().orientation=="portrait") ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    override fun onDestroy() { destroyed=true;network.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy() }
    override fun onUserInteraction() { super.onUserInteraction();if(configDialog?.isShowing==true)resetAdminTimer() }
    private fun resetAdminTimer() { inactiveTimer?.let { handler.removeCallbacks(it) };inactiveTimer=Runnable { configDialog?.dismiss();configDialog=null;message("管理页面已自动锁定") }.also { handler.postDelayed(it,120000) } }
    @Deprecated("Legacy back compatibility") override fun onBackPressed() { authenticate("退出应用","继续退出") { val c=column(24);c.add(label("退出会停止本机状态显示。",16f,muted));c.add(space(20));val d=dialog("管理员退出",c);c.add(button("退出应用") { protect { applyKiosk(false);d.dismiss();finishAndRemoveTask() } }) } }
    private fun render() {
        if(destroyed)return
        val d=store.read();val s=d.snapshot.states.first { it.id==d.snapshot.current };val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val dm=resources.displayMetrics;val wDp=dm.widthPixels/dm.density;val hDp=dm.heightPixels/dm.density
        val k=if(landscape) minOf(wDp/1500f,hDp/843.75f) else minOf(wDp/720f,hDp/1280f)
        fun u(v:Float)=(v*k).toInt()
        fun t(v:Float)=v*k
        val root=column(0).apply { setBackgroundColor(bg) }
        val head=row().apply { setPadding(dp(u(40f)),dp(u(32f)),dp(u(40f)),dp(u(32f))) }
        head.add(label("▥  房间信息",t(16f),text,true).apply { letterSpacing=0.15f },1f)
        connection=if(!d.managed)"单机运行" else if(d.token.isBlank())"尚未注册" else if(connected)"● 服务器已连接" else "○ 离线 · 本地可用"
        head.addView(label(connection,t(16f),if(connected)accent else muted));root.add(head)
        root.add(View(this).apply { setBackgroundColor(Color.rgb(27,42,61));layoutParams=LinearLayout.LayoutParams(-1,dp(1)) })
        val hero=FrameLayout(this)
        hero.addView(View(this).apply { background=GradientDrawable().apply { gradientType=GradientDrawable.RADIAL_GRADIENT;colors=intArrayOf(Color.rgb(16,43,53),bg);gradientRadius=dp(u(1150f)).toFloat();setGradientCenter(0.88f,0.45f) } },FrameLayout.LayoutParams(-1,-1))
        val content=LinearLayout(this).apply { orientation=if(landscape)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL;gravity=if(landscape)Gravity.CENTER_VERTICAL else Gravity.START;setPadding(dp(u(if(landscape)64f else 48f)),dp(u(if(landscape)48f else 64f)),dp(u(if(landscape)64f else 48f)),dp(u(if(landscape)48f else 64f))) }
        val info=column(0)
        info.add(label("房间编号",t(16f),muted).apply { letterSpacing=0.25f })
        val mid=column(0).apply { gravity=Gravity.CENTER_VERTICAL }
        mid.add(label(d.snapshot.number,t(if(landscape)170f else 130f),text,false).apply { letterSpacing=0.03f });mid.add(space(u(22f)))
        val name=label(d.snapshot.name,t(if(landscape)52f else 44f),text,true).apply { contentDescription="房间名称，长按两秒进入管理";isClickable=true;isFocusable=true }
        var held:Runnable?=null
        name.setOnTouchListener { v,event -> when(event.action) { MotionEvent.ACTION_DOWN -> { held=Runnable { authenticate("进入管理","进入管理") { settings() } }.also { handler.postDelayed(it,2000) };true };MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { held?.let { handler.removeCallbacks(it) };v.performClick();true };else->true } }
        name.setOnClickListener { }
        mid.add(name)
        info.addView(mid,LinearLayout.LayoutParams(-1,0,1f))
        info.add(View(this).apply { setBackgroundColor(Color.rgb(122,168,197));layoutParams=LinearLayout.LayoutParams(dp(u(56f)),dp(u(3f))) })
        val panel=row().apply { background=drawable(Color.rgb(18,36,43),Color.rgb(53,86,79),28f*k) }
        panel.addView(View(this).apply { setBackgroundColor(color(s.color)) },LinearLayout.LayoutParams(dp(u(4f)),-1).apply { topMargin=dp(u(45f));bottomMargin=dp(u(45f)) })
        val pc=column(0).apply { setPadding(dp(u(36f)),dp(u(40f)),dp(u(40f)),dp(u(40f))) }
        panel.addView(pc,LinearLayout.LayoutParams(0,-1,1f))
        val sh=row();sh.add(label("当前房间状态",t(16f),Color.rgb(189,203,217)),1f)
        sh.addView(TextView(this).apply { text=s.icon;textSize=t(32f);setTextColor(color(s.color));gravity=Gravity.CENTER;background=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.TRANSPARENT);setStroke(dp(1),color(s.color)) };layoutParams=LinearLayout.LayoutParams(dp(u(64f)),dp(u(64f))) })
        pc.add(sh)
        pc.addView(label(s.name,t(if(landscape)92f else 84f),color(s.color),true).apply { gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(-1,0,1f))
        val sw=row().apply { background=drawable(Color.rgb(31,55,59),Color.rgb(72,100,95),12f*k);setPadding(dp(u(22f)),dp(u(18f)),dp(u(22f)),dp(u(18f)));minimumHeight=dp(u(64f));isClickable=true;setOnClickListener { selectState() } }
        sw.add(label("切换房间状态",t(18f),text),1f);sw.addView(label("↗",t(18f),text))
        pc.add(sw)
        if(landscape) { content.addView(info,LinearLayout.LayoutParams(0,-1,1f));content.addView(space(1),LinearLayout.LayoutParams(dp(u(48f)),1));content.addView(panel,LinearLayout.LayoutParams(0,-1,1f)) }
        else { content.addView(info,LinearLayout.LayoutParams(-1,0,1f));content.add(space(u(56f)));content.addView(panel,LinearLayout.LayoutParams(-1,0,1f)) }
        val scroller=ScrollView(this).apply { isFillViewport=true;addView(content,FrameLayout.LayoutParams(-1,-1)) }
        hero.addView(scroller,FrameLayout.LayoutParams(-1,-1))
        root.addView(hero,LinearLayout.LayoutParams(-1,0,1f))
        root.add(View(this).apply { setBackgroundColor(Color.rgb(27,42,61));layoutParams=LinearLayout.LayoutParams(-1,dp(1)) })
        val footer=row().apply { setPadding(dp(u(40f)),dp(u(24f)),dp(u(40f)),dp(u(24f))) };footer.add(label("本机数据 · 已保存",t(14f),muted),1f);root.add(footer)
        setContentView(root)
    }
    private fun selectState() {
        val body=column(24);body.add(label("选择后立即生效，并保存在本机。",14f,muted));body.add(space(20));val d=dialog("切换房间状态",body)
        store.read().snapshot.states.forEach { s -> body.add(button("${s.icon}   ${s.name}") { protect { val latestData=store.read();val latest=latestData.snapshot;require(latest.states.any { it.id==s.id }) { "状态配置已更新，请重新选择" };store.changeSnapshot(latest.copy(current=s.id),latestData.revision);d.dismiss();render() } });body.add(space(10)) }
    }
    private fun passwordSetup(change:Boolean) {
        val body=column(24);body.add(label(if(change)"设置新的本机管理员密码。" else "首次使用请设置本机管理员密码。状态切换无需密码。",14f,muted));body.add(space(16))
        val old=if(change)body.field("当前密码",password=true)else null
        val password=body.field("新密码（至少6位）",password=true);val confirm=body.field("确认新密码",password=true);val error=label("",14f,Color.rgb(255,174,163));body.add(error)
        val d=dialog(if(change)"修改管理密码" else "设置管理密码",body,change)
        if(!change)d.getButton(AlertDialog.BUTTON_NEGATIVE).visibility=View.GONE
        body.add(button("保存密码",true) { try { if(old!=null)require(store.verify(old.text.toString())) { "当前密码不正确" };require(password.text.toString()==confirm.text.toString()) { "两次密码不一致" };store.setPassword(password.text.toString());d.dismiss();message("管理密码已保存") } catch(e:Exception) { error.text=e.message } })
    }
    private fun authenticate(title:String,ok:String,action:()->Unit) {
        if(authDialog?.isShowing==true)return
        if(!store.passwordSet()) { passwordSetup(false);return }
        val body=column(24);body.add(label("此操作需要管理员认证。",14f,muted));body.add(space(16));val p=body.field("管理密码",password=true);val error=label("",14f,Color.rgb(255,174,163));body.add(error);val d=dialog(title,body);authDialog=d
        body.add(button(ok,true) { if(store.verify(p.text.toString())) { d.dismiss();action() } else { error.text="密码不正确，请重新输入";p.setText("") } })
    }
    private fun settings() {
        val base=store.read();var states=base.snapshot.states.toMutableList()
        val body=column(24);body.add(label("以下信息保存在当前设备。断网时仍可独立编辑。",14f,muted));body.add(space(20));body.add(label("房间信息",20f,text,true));body.add(space(16));val number=body.field("房间编号",base.snapshot.number,max=24);val name=body.field("房间名称",base.snapshot.name,max=80)
        body.add(label("本机状态配置",20f,text,true));body.add(label("删除当前状态前，请先在展示页切换到其他状态。",13f,muted));body.add(space(12));val rows=column();body.add(rows)
        fun drawRows() {
            rows.removeAllViews();states.forEach { s -> val r=row();r.add(label("${s.icon}  ${s.name}",16f,color(s.color)),1f);r.addView(button("编辑") { editState(s) { updated -> states=states.map { if(it.id==s.id)updated else it }.toMutableList();drawRows() } });r.addView(button("删除") { if(s.id==base.snapshot.current)message("不能删除当前使用的状态") else { states.removeAll { it.id==s.id };drawRows() } });rows.add(r);rows.add(space(8)) }
        };drawRows();body.add(button("＋ 添加状态") { if(states.size>=30)message("最多配置30个状态") else editState(RoomState(UUID.randomUUID().toString(),"","#83DFC2","◇")) { states.add(it);drawRows() } });body.add(space(24))
        body.add(label("服务器连接",20f,text,true));body.add(label("单机无需填写。联网时使用本机所在网络的服务器地址。",13f,muted));val managed=CheckBox(this).apply { text="连接服务器";setTextColor(this@MainActivity.text);isChecked=base.managed };body.add(managed)
        val server=body.field("服务器地址",base.server,max=250)
        val orientation=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("横屏","竖屏"));setSelection(if(base.orientation=="portrait")1 else 0) }
        body.add(label("屏幕方向",14f,muted));body.add(orientation);body.add(space(16))
        val lock=CheckBox(this).apply { text="启用专用设备锁定（需设备所有者权限）";setTextColor(this@MainActivity.text);isChecked=base.lock };body.add(lock)
        body.add(label("持续亮屏已开启。启用锁定后拦截系统主页、多任务、通知栏和电源菜单，仅可经管理密码退出；需先按部署说明授予设备所有者权限。",13f,muted));body.add(space(12));body.add(label("设备编号：${base.deviceId}",12f,muted));body.add(label(if(base.token.isNotEmpty())"设备凭据：已注册（不显示密钥）" else "设备凭据：尚未注册",13f,muted));if(lastError.isNotEmpty())body.add(label("最近连接结果：$lastError",13f,Color.rgb(255,193,142)))
        val error=label("",14f,Color.rgb(255,174,163));body.add(error);val d=dialog("本机设置",body);configDialog=d;resetAdminTimer();d.setOnDismissListener { inactiveTimer?.let { handler.removeCallbacks(it) };configDialog=null }
        body.add(button("保存本机设置",true) { try {
            val address=server.text.toString().trim().trimEnd('/')
            if(managed.isChecked) { val uri=URI(address);require(uri.scheme in listOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo==null && (uri.path.isNullOrEmpty() || uri.path=="/") && uri.query==null && uri.fragment==null) { "请输入完整服务器地址，例如 http://192.168.1.100:8080，不含路径" } }
            if(lock.isChecked)require(isOwner()) { "设备尚未授予设备所有者权限，不能启用系统锁定" }
            require(store.read().revision==base.revision) { "状态已被远程修改，请关闭并重新打开设置后编辑" }
            store.settings(base.snapshot.copy(number=number.text.toString().trim(),name=name.text.toString().trim(),states=states),address,managed.isChecked,if(orientation.selectedItemPosition==1)"portrait" else "landscape",lock.isChecked,base.revision)
            connected=false;d.dismiss();applyOrientation();applyKiosk(lock.isChecked);render();if(managed.isChecked && store.read().token.isBlank())registration()else message("本机设置已保存")
        } catch(e:Exception) { error.text=e.message } });body.add(space(12));body.add(button("注册 / 查看连接") { d.dismiss();registration() });body.add(space(12));body.add(button("修改管理密码") { d.dismiss();passwordSetup(true) });body.add(space(12));body.add(button("退出应用") { d.dismiss();protect { applyKiosk(false);finishAndRemoveTask() } })
    }
    private fun editState(s:RoomState,onSave:(RoomState)->Unit) {
        val c=column(24);val name=c.field("状态名称",s.name,max=16);val hex=c.field("颜色（#RRGGBB）",s.color,max=7);val icon=c.field("图标（如 ✓ ◷ − ◇）",s.icon,max=8);val error=label("",14f,Color.rgb(255,174,163));c.add(error);val d=dialog("编辑状态",c)
        c.add(button("确认",true) { val n=name.text.toString().trim();val h=hex.text.toString().trim();val i=icon.text.toString().trim();if(n.isBlank()||!h.matches(Regex("#[0-9a-fA-F]{6}"))||i.isBlank())error.text="请填写名称、图标及正确的十六进制颜色"else { onSave(s.copy(name=n,color=h,icon=i));d.dismiss() } })
    }
    private fun registration() {
        val local=store.read();val body=column(24)
        body.add(label("服务器：${local.server.ifBlank { "未设置" }}",14f,muted));body.add(label("设备编号：${local.deviceId}",13f,muted));body.add(space(12))
        if(!local.managed || local.server.isBlank()) { body.add(label("请先在本机设置中启用连接服务器并保存地址。",16f));dialog("服务器连接",body);return }
        if(local.token.isNotBlank()) {
            body.add(label(if(connected)"连接正常" else "暂未连接，后台会自动重试",20f,if(connected)accent else text));body.add(label(lastError.ifBlank { "已保存设备凭据" },14f,muted));body.add(space(16));val d=dialog("服务器连接",body)
            body.add(button("重新注册为新设备") { AlertDialog.Builder(this).setTitle("重新注册？").setMessage("将生成新的设备编号。服务器保留旧记录，新设备需要再次输入注册密钥；仅用于更换服务器或凭据失效。").setNegativeButton("取消",null).setPositiveButton("继续") { _,_->store.newIdentity();connected=false;d.dismiss();registration() }.show() });return
        }
        body.add(label("输入服务器管理员提供的设备注册密钥。注册成功后密钥不在设备保存。",14f,muted));body.add(space(12));val key=body.field("设备注册密钥",password=true,max=256);val info=label("",14f,Color.rgb(255,193,142));body.add(info);val d=dialog("注册设备",body)
        val register=button("注册并连接",true) { };register.setOnClickListener {
            if(registering)return@setOnClickListener
            val enrollment=key.text.toString();if(enrollment.isBlank()) { info.text="请输入注册密钥";return@setOnClickListener }
            registering=true;register.isEnabled=false;info.text="正在注册…"
            network.execute { try { val response=Api.post(local.server,"/api/device/register",JSONObject().put("deviceId",local.deviceId).put("enrollmentKey",enrollment));store.setToken(local.deviceId,local.server,response.getString("token"));handler.post { if(!destroyed) { d.dismiss();message("注册成功，正在同步房间状态");render() } } } catch(e:Exception) { handler.post { if(!destroyed) { info.text=e.message ?: "注册失败";register.isEnabled=true } } } finally { registering=false } }
        };body.add(register)
    }
    private fun sync() {
        if(destroyed)return
        val sent=store.read()
        if(!sent.managed||sent.server.isBlank()||sent.token.isBlank())return
        try {
            val request=JSONObject().put("revision",sent.revision).put("snapshot",sent.snapshot.json());sent.ack?.let { request.put("ack",it) }
            val response=Api.post(sent.server,"/api/device/sync",request,sent.deviceId,sent.token)
            val changed=store.handleResponse(sent,response)
            handler.post { if(!destroyed) { val now=store.read();if(now.deviceId==sent.deviceId && now.server==sent.server && now.managed) { val was=connected;connected=true;lastSuccessful=System.currentTimeMillis();lastError="";if(changed||!was)render() } } }
        } catch(e:Exception) {
            handler.post { if(!destroyed) { lastError=e.message ?: "连接失败";val was=connected;connected=false;if(was)render() } }
        }
    }
    private fun isOwner()=(getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isDeviceOwnerApp(packageName)
    private fun lockTaskActive()=(getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState!=ActivityManager.LOCK_TASK_MODE_NONE
    private fun applyKiosk(enabled:Boolean,notify:Boolean=true) {
        try {
            if(enabled) {
                check(isOwner()) { "未取得设备所有者权限" }
                val manager=getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin=ComponentName(this,RoomAdminReceiver::class.java)
                manager.setLockTaskPackages(admin,arrayOf(packageName))
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) manager.setLockTaskFeatures(admin,DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
                if(!lockTaskActive()) startLockTask()
            } else if(lockTaskActive()) stopLockTask()
        } catch(e: Exception) { if(notify) message(e.message ?: "操作失败") }
    }
}


