# 房间状态服务器

Java 8+ 运行 / JDK 17 构建 / Spring Boot 2.7.18 / Maven / H2 文件数据库。管理端源码在 `web/`（Vue 3），已集成进 Maven 构建：`mvn package` 自动构建管理端并打包进同一 JAR 的静态资源，与 API 同源；运行时无需 Node 运行环境。

## 构建

构建自动包含管理端：下载Node、安装`web/`前端依赖、运行前端单元测试，并把网页构建进JAR的static资源。仅改后端时可加`-Dskip.web=true`跳过前端构建（JAR将不含管理页）。

```text
mvn clean verify
java -jar target/room-server-1.0.0.jar
```

启动前必须配置：

| 环境变量 | 作用 |
|---|---|
| ROOM_ADMIN_PASSWORD | 首次初始化管理员密码，至少8字符，UTF-8不超过72字节 |
| ROOM_ENROLLMENT_KEY | 设备注册密钥初始值，至少12字符，建议随机生成32字符以上；仅首次建库写入，之后在管理端「系统设置」查看/修改 |
| PORT | 默认8080 |
| ROOM_DATABASE_URL | 默认 `jdbc:h2:file:./data/rooms;DB_CLOSE_ON_EXIT=FALSE` |
| ROOM_DATABASE_PASSWORD | H2密码，可选；首次建库时确定，随后必须保持一致 |

默认管理员用户名 `admin`。管理员密码修改后持久保存，重启不会被初始环境变量覆盖。服务器要求两项初始配置持续提供，避免误启动。设备注册密钥与管理员密码是不同用途，不应共用。注册密钥与管理员密码一样持久保存：首次建库写入后，在管理端「系统设置 · 设备接入」查看与更换，重启不会被启动参数覆盖。

Windows PowerShell 示例（请替换尖括号内容）：

```powershell
$env:ROOM_ADMIN_PASSWORD='<你设置的管理员密码>'
$env:ROOM_ENROLLMENT_KEY='<随机设备注册密钥>'
java -jar .\target\room-server-1.0.0.jar
```

Linux shell 示例：

```sh
export ROOM_ADMIN_PASSWORD='<你设置的管理员密码>'
export ROOM_ENROLLMENT_KEY='<随机设备注册密钥>'
java -jar room-server-1.0.0.jar
```

请在固定工作目录启动，默认数据库在该目录的data下。不要让多个服务器进程打开同一文件数据库。备份时停止服务后备份整个data目录及部署配置，避免只复制部分数据库文件。

## 网络

浏览器访问 `http://服务器IP:8080`。Android模拟器访问主机使用 `http://10.0.2.2:8080`；真实设备填写局域网服务器IP。设备和管理端通过同一端口访问。默认监听网络接口，部署时仅向需要的可信局域网放行8080端口。

HTTP适用于隔离、可信的局域网测试；不对公网直接开放。生产可通过Nginx等反向代理提供HTTPS，Android端填入对应HTTPS地址并使用可信证书。

## 协议与数据一致性

完整接口见上级API-CONTRACT.md。每设备只允许一条待确认指令，设备15秒无心跳判定离线，指令15秒过期。过期/离线指令不排队重放。服务器重启使待确认指令失效，并将设备连接标记为待重新确认。

设备revision为权威单调序号。相同revision不能上传不同快照，低版本不能覆盖高版本。指令包含创建时baseRevision，设备本地更新导致baseRevision不符时服务端标记CONFLICT。设备保存成功后携带APPLIED确认；重复确认幂等。延迟收到的成功确认若包含匹配设备版本可以更新EXPIRED记录为实际APPLIED，防止把设备已经执行的指令一直记作失败；不会因此重新下发指令。

房间编号/名称、状态目录和当前状态作为一个快照保存。模板下发是显式命令，不在模板编辑时自动覆盖设备。

设备注册使用唯一deviceId，已注册编号不得用注册密钥接管，服务端只存设备token的SHA-256摘要。不要克隆已注册设备的数据目录。清除应用数据会生成新设备身份，旧设备记录保留以供审计；当前版本未提供设备删除或凭据恢复界面。

管理会话保存在服务器内存，12小时有效，退出或重启失效。修改密码后撤销全部会话。密码以BCrypt存储，登录按来源IP限制失败尝试。日志API返回最近1000条，数据库保留所有记录。

## 测试

`mvn test` 使用隔离的内存H2测试库，不接触生产data。覆盖身份认证、重复注册、防止过期/离线命令、revision冲突、成功确认幂等、状态验证、并发待确认保护及模板入库。
