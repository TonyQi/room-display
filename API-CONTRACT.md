# 三端接口契约 v1
根目录 outputs/room-display，子目录 server / android / web / docs。
Java17 Spring Boot；Kotlin 原生Android；Vue3 Vite管理端（server/web/），由Maven构建自动打包进同一JAR的静态资源。
所有API /api，JSON UTF8；错误 {message:string}；时间epoch毫秒。
管理员：POST /api/auth/login {username,password} -> {token}；Authorization: Bearer token。GET /api/auth/me -> {username}。POST /api/auth/password {oldPassword,newPassword}。
启动通过环境ROOM_ADMIN_PASSWORD设初始密码（至少8字符），ROOM_ENROLLMENT_KEY设设备注册密钥初始值。默认用户名admin。服务器未配置必须拒绝启动，不生成已知生产密码。两项仅在首次建库时写入数据库，之后修改：密码走POST /api/auth/password，注册密钥走下述管理端设置接口，均持久保存。
房间快照 snapshot={number:string,name:string,states:[{id:string,name:string,color:'#RRGGBB',icon:string}],current:string} current引用states[].id。每次设备本地或远程成功修改均在设备事务内revision++。
设备首次使用生成deviceId UUID，注册POST /api/device/register {deviceId,enrollmentKey} -> {token}。每设备token长期保存，同deviceId重复注册需避免接管（拒绝重复）。设备header X-Device-Id, X-Device-Token。
设备轮询POST /api/device/sync {revision:long,snapshot,ack?:{id:string,result:'APPLIED'|'CONFLICT'|'FAILED',message:string}} -> {command:null|{id,baseRevision:long,expiresAt:long,snapshot}}。2秒轮询。设备只在当前revision==baseRevision且未过期时保存指令快照，revision++，保存ack供下次上传，失败也ack。网络读取期间本地可能修改，应用指令前必须重新检查revision。设备修改与网络应用串行化。服务器每次sync先处理ack并更新快照，再决定下发指令。服务器依据当前sync revision与baseRevision判定冲突，不重放旧指令。ack响应丢失设备重复ack应幂等；设备保存appliedCommandId避免重复应用。服务器15秒无心跳离线，指令15秒过期。离线拒绝发指令，每设备最多一个待确认。
GET /api/admin/devices -> [{deviceId,revision,snapshot,lastSeen,online,pending:boolean}]
POST /api/admin/devices/{deviceId}/commands {baseRevision,snapshot} -> {id,status:'PENDING'}。服务器验证revision、在线、无pending，冲突409。房间编辑/切换状态/模板都发完整快照。
GET /api/admin/logs -> [{id,deviceId,createdAt,status:'PENDING'|'APPLIED'|'CONFLICT'|'FAILED'|'EXPIRED',message,snapshot}]
GET /api/admin/templates -> [{id,name,states}]
POST /api/admin/templates {id?:string,name,states} -> 保存模板；DELETE /api/admin/templates/{id}
GET /api/admin/settings/enrollment-key -> {enrollmentKey}；POST /api/admin/settings/enrollment-key {enrollmentKey}（12-255字符）更换注册密钥，旧密钥对新注册立即失效，已注册设备不受影响。
GET /api/health -> {status:'UP'}不认证。
SQLite或H2本地持久数据库，服务重启保留设备/模板/日志/管理员密码，待执行指令重启应失效。
服务器使用8080，Android模拟器访问http://10.0.2.2:8080，本地浏览器http://localhost:8080。局域网可HTTP，文档明确受信局域网并建议HTTPS反代。不需要外网资源或CDN。
Android单机首次设置管理员密码；房间配置/服务器地址/注册密钥/方向/密码修改需管理员认证，状态切换免密。密码安全哈希存储。支持持久化本地状态、长按名称管理、持续亮屏、全屏、可选择启用设备所有者LockTask，普通应用无法承诺系统级防退出。横竖屏与原型保持一致。联网未注册时提供注册密钥输入；不得把服务器管理员密码输入设备。
