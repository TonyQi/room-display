# 房间状态显示系统

本项目包含可独立运行的Android设备应用、Java服务和浏览器管理端。设备在同一局域网连接服务；支持20–30台设备及继续添加。既有界面原型单独保留。

## 目录
- `android/`：Kotlin原生应用源码和Gradle构建文件。
- `server/`：合并工程。Java17 Spring Boot服务与Vue3管理端（源码在`server/web/`），一条Maven命令构建出包含管理端网页的单个JAR；含H2数据库初始化、接口与前端测试。
- `deploy/`：Windows/Linux启动脚本、Linux可选systemd示例；把构建出的JAR复制为同目录`room-server.jar`后运行。
- `docs/`：设计、联调结果、部署与验收说明。
- `API-CONTRACT.md`：跨端接口和同步约定。

## Linux启动
需要安装Java 8或更新的JDK。将deploy目录复制到服务器独立目录（如`/opt/room-display`），把构建产物`server/target/room-server-1.0.0.jar`复制到该目录并命名为`room-server.jar`，在该目录执行：

```sh
cp room.env.example room.env
# 编辑room.env，替换管理员初始密码和设备注册密钥
chmod 600 room.env
chmod +x start-linux.sh
./start-linux.sh
```

`ROOM_ADMIN_PASSWORD`至少8字符且UTF8不超过72字节；`ROOM_ENROLLMENT_KEY`至少12字符，建议随机32位以上。不要使用示例占位文字。初始用户名`admin`。浏览器访问`http://服务器IP:8080`。仅在首次创建数据库时使用环境中的初始管理员密码，之后修改密码应使用后台系统设置；注册密钥同样仅首次建库写入，可在后台「系统设置 · 设备接入」查看与更换。

服务器监听8080，系统防火墙按实际局域网需要放行该端口；不自动修改防火墙。H2数据库位于启动工作目录下`data/rooms.mv.db`。不要启动第二个进程同时使用同一数据库。备份前正常停止服务，再复制整个data目录；恢复时同时保留设备原应用数据。

`room-display.service`只是可选示例，不会自动安装或设置开机启动。如使用，应先创建专用非root用户、配置目录权限并根据实际路径修改单元文件。

## Windows启动
安装Java 8或更新的JDK后，把构建产物`server/target/room-server-1.0.0.jar`复制为deploy目录下的`room-server.jar`，在deploy目录的PowerShell设置仅当前进程使用的环境变量再启动：

```powershell
$env:ROOM_ADMIN_PASSWORD='请替换为强密码'
$env:ROOM_ENROLLMENT_KEY='请替换为足够长的随机设备注册密钥'
.\start-windows.ps1
```

浏览器访问`http://localhost:8080`。开发联调使用Windows，部署包也适用于Linux；无需Node运行环境即可运行打包后的服务和网页。

## Android安装与接入
安装交付的联调版APK，首次设置本机管理员密码。长按房间名称2秒进入本机设置。可先独立编辑房间和状态，完全不需要服务器。

联网时启用“连接服务器”，输入`http://服务器局域网IP:8080`，保存后输入管理员配置的设备注册密钥。官方安卓模拟器访问本机服务器使用`http://10.0.2.2:8080`。注册成功并同步后管理端自动出现设备，无需手工创建演示记录。

设备本地密码、后台admin密码、设备注册密钥用途不同。设备注册密钥只用于注册，可在管理端「系统设置 · 设备接入」查看与更换；返回的独立设备凭据保存在应用私有存储。更换服务器或清除应用数据会产生新身份；后台保留旧设备记录，避免误认设备。

## 同步行为
每次本地修改先持久保存，并增加版本号。后台操作只有设备确认保存后才标记已生效。设备每2秒同步，15秒没有心跳视为离线；离线或已有待处理操作时拒绝新指令。旧版本和过期操作不覆盖本地新数据。模板保存不直接改变设备，下发时明确选择替换的状态目录和当前状态。

同一局域网应保持服务器与设备日期时间正确（建议自动校时）；设备会检查指令过期时间。HTTP方式用于受信局域网，跨不可信网络请使用HTTPS反向代理及可信证书；APK不跳过证书校验。

## 重建源码
JDK17和Maven3.9（构建用；产物为Java 8字节码，Java 8及以上即可运行）、Android SDK35/Build Tools35.0.0。管理端不需要预装Node：Maven构建会自动下载Node、安装前端依赖并构建网页（首次构建需联网）。

```sh
cd server
mvn clean package
cd ../android
# 设置ANDROID_HOME，或在local.properties配置sdk.dir
./gradlew assembleDebug
```

Windows使用`gradlew.bat`。`mvn clean package`一条命令完成管理端依赖安装、单元测试、网页构建、Java测试和打包；JAR输出server/target/room-server-1.0.0.jar，管理端网页已包含在JAR内，启动后同一端口同时提供后台页面和API，只需部署这一个JAR。Android输出app/build/outputs/apk/debug/app-debug.apk。源码不包含SDK、JDK、node_modules、私有签名密钥或本机测试账户。

交付APK使用Android调试签名，供安装联调；正式大批量分发前请用组织自己的签名密钥构建release，并长期保管该密钥，以保证后续覆盖升级。Android版本尚未知，候选最低支持Android6.0，需在实际设备确认。

## 防退出
前台持续亮屏、沉浸式显示、密码保护应用内退出已实现。系统级防退出（拦截系统主页、多任务、通知栏和电源菜单）通过设备所有者权限加LockTask锁定实现：普通APK不能自行获得该权限，需部署人员用adb授予设备所有者后，在本机设置启用“专用设备锁定”，详见android/README.md。锁定仅保留系统信息（时钟/电量），应用回到前台时自动恢复锁定，管理员凭密码经“退出应用”退出。已在官方模拟器（Android 11）验证主页与多任务键被拦截（docs/android-防退出锁定.png）。实际13.3英寸设备的厂家固件仍需实机验证，未验证前不标记为已通过实机防退出验收。
