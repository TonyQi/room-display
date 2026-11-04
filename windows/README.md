# Windows 11 一体化管理中心

交付对象是 Windows 11 x64 免安装文件夹：解压后双击 `RoomDisplay.exe`。内置界面、Java设备通信服务与运行环境，不需要另行部署服务器或安装Java/Node。现有安卓APK与通信接口保持兼容。

## 使用
首次运行设置管理密码，默认账户为 `admin`，默认连接端口8080。登录后在“接入设备”查看本机局域网地址，在“系统设置”查看设备注册密钥。安卓端继续使用现有设置页，在“服务器地址”填写这里显示的管理电脑地址。

关闭窗口会收至托盘，集中管理继续运行；双击托盘图标恢复。通过托盘“退出管理中心”可停止整个程序。没有设置开机自启动。

所有管理数据保存在EXE旁的 `data` 文件夹。完整解压至可写目录，勿只移动EXE。更新时退出程序，保留原 `data`，替换其他程序文件。一个数据目录只能由一个管理实例使用。

系统设置支持备份：选择程序data目录之外的位置，备份时会短暂停止设备通信，再自动恢复；随后重新登录。备份包含 `service` 和 `config.json`。恢复须先退出程序，保留现有data的副本，再把这两项复制回data。不要在线复制正在使用的H2数据库。

首次出现Windows防火墙提示时，仅允许实际使用的受信任局域网。若设备无法连接，检查电脑IP、端口和专用网络防火墙规则；程序不擅自更改防火墙，也不申请管理员权限。电脑关机或完全退出程序后，安卓仍可独立操作。

## 构建源码
Windows x64、Node 22.12+、Maven、JDK17用于构建。先在 `server/web` 执行 `npm ci && npm run build`，再在 `server` 执行 `mvn -Dskip.web=true package`。然后在本目录执行：

```powershell
npm ci
node node_modules/electron/install.js
.\scripts\prepare-runtime.ps1
npm test
npm run package
```

输出在 `build/RoomDisplay-win32-x64`。构建包不含用户数据库或预设管理密码。源码中的Java服务只作为EXE内置组件，不向用户单独交付部署包。

## 实现说明
Electron renderer启用隔离与安全沙箱、禁用Node能力；仅允许本机应用页面调用限定桌面桥。Windows窗口通过本机回环地址访问内置Java服务；安卓沿用局域网HTTP接口。服务关闭由父进程标准输入管道控制，没有远程关机接口。父进程异常退出也会关闭数据库并终止Java子进程。

使用Electron、Microsoft OpenJDK、Spring Boot、H2与Vue。分发目录保留Electron许可证与JRE legal目录。本版未使用商业代码签名证书，Windows可能显示未知发布者；不要关闭系统安全防护。
