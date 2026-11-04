const {app,BrowserWindow,ipcMain,Tray,Menu,nativeImage,dialog,shell}=require('electron');
const {spawn}=require('node:child_process');
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const crypto=require('node:crypto');
const {validateSetup,validPort,connectionAddresses,readConfig,saveConfig,assertPortFree,copyTree}=require('./core.cjs');

const baseDir=app.isPackaged?path.dirname(app.getPath('exe')):path.resolve(__dirname,'..','dev-data');
const dataDir=path.join(baseDir,'data');
const resourceDir=app.isPackaged?process.resourcesPath:path.resolve(__dirname,'..','runtime-resources');
let bootError='';
try{fs.mkdirSync(dataDir,{recursive:true});fs.accessSync(dataDir,fs.constants.W_OK);}catch{bootError='程序目录无法写入。请将整个压缩包解压到可写目录，例如 D:\\房间状态管理，再运行 EXE。';}
app.setPath('userData',path.join(dataDir,'browser'));
const ownsLock=app.requestSingleInstanceLock();
if(!ownsLock){app.quit();}else{
  let win,tray,child,config=null,firstRun=true,busy=false,quitting=false,quitDialog=false,serviceReady=false,blocked=false;
  let status={stage:'starting',message:'正在准备管理中心…',error:'',firstRun:false,port:8080};
  let currentLog=null;
  const startupFile=path.join(__dirname,'startup.html');
  const startupURL=pathToFileURL(startupFile).href;
  const origin=()=>`http://127.0.0.1:${config?.port||8080}`;
  const secret=()=>crypto.randomBytes(24).toString('base64url');
  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  function send(update){status={...status,...update,firstRun,port:config?.port||status.port};if(win&&!win.isDestroyed())win.webContents.send('desktop:status',status);}
  function show(){if(!win)return;if(win.isMinimized())win.restore();win.show();win.focus();}
  function ensureCaller(event,setupOnly=false){
    if(!win||event.sender!==win.webContents||event.senderFrame!==win.webContents.mainFrame)throw new Error('无效请求来源');
    const url=event.senderFrame.url;
    if(url!==startupURL&&(setupOnly||!url.startsWith(origin()+'/')))throw new Error('不允许此页面操作本机程序');
  }
  function makeWindow(){
    win=new BrowserWindow({width:1440,height:960,minWidth:1000,minHeight:700,title:'房间状态管理中心',backgroundColor:'#0a111b',show:false,icon:path.join(__dirname,'icon.png'),webPreferences:{preload:path.join(__dirname,'preload.cjs'),nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,devTools:!app.isPackaged,spellcheck:false}});
    win.removeMenu();win.once('ready-to-show',show);
    win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
    win.webContents.on('will-navigate',(event,url)=>{if(url!==startupURL&&!url.startsWith(origin()+'/'))event.preventDefault();});
    win.webContents.session.setPermissionRequestHandler((wc,permission,callback)=>callback(false));
    win.on('close',event=>{if(!quitting){event.preventDefault();win.hide();if(tray)tray.displayBalloon({title:'房间状态仍在运行',content:'设备连接继续保持。双击托盘图标可恢复窗口；退出请使用托盘菜单。',respectQuietTime:true});}});
    win.on('session-end',()=>{quitting=true;child?.stdin.end();});
    win.webContents.on('did-finish-load',()=>send({}));
    return win.loadFile(startupFile);
  }
  async function startupPage(){if(win.webContents.getURL()!==startupURL)await win.loadFile(startupFile);send({});show();}
  function makeTray(){
    const image=nativeImage.createFromPath(path.join(__dirname,'icon.png')).resize({width:24,height:24});
    tray=new Tray(image);tray.setToolTip('房间状态管理中心 · 后台服务运行中');
    tray.setContextMenu(Menu.buildFromTemplate([
      {label:'打开管理中心',click:show},
      {label:'打开数据目录',click:()=>shell.openPath(dataDir)},
      {label:'备份数据…',click:()=>backup().catch(e=>dialog.showErrorBox('备份失败',e.message))},
      {type:'separator'},
      {label:'退出管理中心',click:requestQuit}
    ]));tray.on('double-click',show);
  }
  async function stopService(){
    serviceReady=false;const p=child;if(!p)return;
    p.expectedStop=true;
    const done=new Promise(resolve=>{if(p.exitCode!==null)resolve();else p.once('exit',resolve);});
    try{p.stdin.end('shutdown\n');}catch{}
    await Promise.race([done,sleep(10000)]);
    if(p.exitCode===null&&!p.killed){p.kill();await Promise.race([done,sleep(3000)]);}
    if(child===p)child=null;
  }
  async function startService(password,navigate=true){
    if(blocked||quitting)throw new Error('当前不能启动，请按提示恢复数据');
    if(busy)throw new Error('正在启动或备份，请稍后再试');busy=true;
    try{
      await stopService();await assertPortFree(config.port);
      const java=path.join(resourceDir,'jre','bin','java.exe'),jar=path.join(resourceDir,'room-server.jar');
      if(!fs.existsSync(java)||!fs.existsSync(jar))throw new Error('运行文件不完整。请重新完整解压，勿单独移动 EXE。');
      const serviceDir=path.join(dataDir,'service'),logs=path.join(dataDir,'logs');fs.mkdirSync(serviceDir,{recursive:true});fs.mkdirSync(logs,{recursive:true});
      for(const old of fs.readdirSync(logs).filter(f=>/^service-.*\.log$/.test(f)).sort().slice(0,-4))fs.unlinkSync(path.join(logs,old));
      currentLog=path.join(logs,`service-${Date.now()}.log`);
      const log=fs.createWriteStream(currentLog,{flags:'a'});log.on('error',()=>{});
      const instance=secret();
      send({stage:'starting',message:'正在启动本机设备通信服务…',error:''});
      const environment={...process.env,PORT:String(config.port),ROOM_ADMIN_PASSWORD:password||secret(),ROOM_ENROLLMENT_KEY:secret(),ROOM_DESKTOP_MODE:'true',ROOM_INSTANCE_TOKEN:instance,ROOM_DATABASE_URL:'jdbc:h2:file:./rooms;DB_CLOSE_ON_EXIT=FALSE',ROOM_DATABASE_PASSWORD:''};
      // This application always uses its own portable runtime and database.
      for(const key of Object.keys(environment))if(key.startsWith('SPRING_')||['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH','SERVER_PORT','SERVER_ADDRESS'].includes(key))delete environment[key];
      const p=spawn(java,['-Xms64m','-Xmx384m','-Dfile.encoding=UTF-8','-jar',jar],{cwd:serviceDir,env:environment,windowsHide:true,stdio:['pipe','pipe','pipe']});child=p;
      p.stdout.pipe(log,{end:false});p.stderr.pipe(log,{end:false});p.stdin.on('error',()=>{});
      let spawnError=null;p.once('error',e=>{spawnError=e;log.end();});
      p.once('exit',()=>{
        log.end();if(child===p)child=null;serviceReady=false;
        if(!p.expectedStop&&!quitting&&status.stage==='running'){
          send({stage:'error',error:'设备通信服务已停止。请重试启动；仍失败时可打开日志查看。',message:'连接已暂停'});
          startupPage().catch(()=>{});
        }
      });
      const deadline=Date.now()+65000;
      while(Date.now()<deadline){
        if(spawnError)throw new Error('内置运行环境启动失败：'+spawnError.message);
        if(p.exitCode!==null)throw new Error('本机服务未能启动，请打开日志查看。现有数据不会被清除。');
        try{const r=await fetch(origin()+'/api/health',{signal:AbortSignal.timeout(1500)});const body=await r.json();if(r.ok&&body.status==='UP'&&body.instance===instance){
          saveConfig(dataDir,config);firstRun=false;serviceReady=true;send({stage:'running',message:'本机服务已就绪',error:''});
          if(navigate)await win.loadURL(origin()+'/');tray?.setToolTip('房间状态管理中心 · 服务运行中');return;
        }}catch{}
        await sleep(400);
      }
      throw new Error('启动等待超时。请检查运行日志和端口设置后重试。');
    }catch(e){await stopService();send({stage:'error',error:e.message,message:'启动未完成'});await startupPage();throw e;}
    finally{busy=false;}
  }
  async function requestQuit(){
    if(quitDialog||quitting)return;quitDialog=true;
    try{
      if(busy){await dialog.showMessageBox(win,{type:'info',message:'正在启动或备份，请完成后再退出。',buttons:['知道了']});return;}
      const result=await dialog.showMessageBox(win,{type:'question',title:'退出管理中心',message:'退出后，这台电脑将停止集中管理。',detail:'安卓设备仍可单机操作；再次运行程序后会恢复连接。',buttons:['继续运行','退出程序'],defaultId:0,cancelId:0,noLink:true});
      if(result.response===1){quitting=true;await stopService();tray?.destroy();app.quit();}
    }finally{quitDialog=false;}
  }
  async function backup(){
    if(busy||!serviceReady)throw new Error('请在服务正常运行时备份');
    busy=true;
    let result;
    try{result=await dialog.showOpenDialog(win,{title:'选择备份保存位置',properties:['openDirectory','createDirectory']});}finally{busy=false;}
    if(result.canceled)return {cancelled:true};
    if(quitting)throw new Error('程序正在退出，已取消备份');
    const parent=path.resolve(result.filePaths[0]);const relative=path.relative(dataDir,parent);
    if(relative===''||(!relative.startsWith('..'+path.sep)&&!path.isAbsolute(relative)))throw new Error('请选择 data 目录之外的备份位置');
    const dest=path.join(parent,'房间状态备份-'+new Date().toISOString().replace(/[:.]/g,'-'));
    busy=true;let error;
    try{
      await stopService();fs.mkdirSync(dest);copyTree(path.join(dataDir,'service'),path.join(dest,'service'));fs.copyFileSync(path.join(dataDir,'config.json'),path.join(dest,'config.json'));
      fs.writeFileSync(path.join(dest,'恢复说明.txt'),'退出管理中心后，将此处 service 文件夹和 config.json 复制回程序的 data 文件夹。恢复前先保留原 data 文件夹备份。备份包含设备凭据和管理数据，请妥善保管。','utf8');
    }catch(e){error=e;}
    finally{busy=false;if(!quitting)await startService(undefined,false);}
    if(error)throw error;return {cancelled:false,path:dest};
  }
  const handle=(name,fn,setupOnly=false)=>ipcMain.handle(name,async(event,...args)=>{ensureCaller(event,setupOnly);return fn(...args);});
  handle('desktop:info',()=>({addresses:connectionAddresses(config?.port||8080),port:config?.port||8080,dataDir,version:app.getVersion(),...status}));
  handle('desktop:setup',async input=>{if(blocked)throw new Error('请先恢复数据与配置');if(!firstRun)throw new Error('此电脑已经完成初始化');const value=validateSetup(input);config={version:1,port:value.port};await startService(value.password);return {ok:true};},true);
  handle('desktop:retry',async port=>{if(blocked)throw new Error('请先恢复数据与配置');if(firstRun)throw new Error('请先设置管理密码');config={version:1,port:validPort(port)};await startService();return {ok:true};},true);
  handle('desktop:open-data',()=>shell.openPath(dataDir));
  handle('desktop:open-logs',()=>{const d=path.join(dataDir,'logs');fs.mkdirSync(d,{recursive:true});return shell.openPath(d);});
  handle('desktop:backup',backup);handle('desktop:quit',requestQuit);
  app.on('second-instance',show);
  app.on('window-all-closed',()=>{});
  app.on('before-quit',event=>{if(!quitting){event.preventDefault();requestQuit();}});
  app.whenReady().then(async()=>{
    await makeWindow();makeTray();
    if(bootError){blocked=true;send({stage:'blocked',error:bootError});return;}
    try{
      config=readConfig(dataDir);
      const databaseExists=fs.existsSync(path.join(dataDir,'service','rooms.mv.db'));
      if(!config&&databaseExists)throw new Error('检测到已有数据库但缺少配置。请从备份恢复 data/config.json，避免误初始化。');
      if(config&&!databaseExists)throw new Error('检测到配置但数据库缺失。请从备份恢复 data/service，程序不会自动新建空库覆盖原环境。');
      firstRun=!config;
    }catch(e){blocked=true;send({stage:'blocked',message:'需要恢复本机数据',error:e.message});return;}
    if(firstRun)send({stage:'setup',message:'欢迎使用房间状态管理中心',error:''});
    else try{await startService();}catch{}
  });
}
