const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const net=require('node:net');

function validPort(value){const port=Number(value);if(!Number.isInteger(port)||port<1024||port>65535)throw new Error('端口须为 1024–65535 的整数');return port;}
function validateSetup(input){
  if(!input||typeof input.password!=='string'||input.password.length<8||Buffer.byteLength(input.password,'utf8')>72)throw new Error('管理密码至少 8 位，且不超过 72 字节');
  if(input.password!==input.confirm)throw new Error('两次密码不一致');
  return {password:input.password,port:validPort(input.port)};
}
function connectionAddresses(port,interfaces=os.networkInterfaces()){
  return [...new Set(Object.values(interfaces).flat().filter(n=>n&&n.family==='IPv4'&&!n.internal&&!n.address.startsWith('169.254.')).map(n=>`http://${n.address}:${port}`))];
}
function readConfig(dir){
  const filename=path.join(dir,'config.json');if(!fs.existsSync(filename))return null;
  try{const c=JSON.parse(fs.readFileSync(filename,'utf8'));if(c.version!==1)throw new Error();return {version:1,port:validPort(c.port)};}
  catch{throw new Error('本机配置文件无法读取。请从备份恢复 data/config.json，不要删除房间数据库。');}
}
function saveConfig(dir,config){fs.mkdirSync(dir,{recursive:true});const file=path.join(dir,'config.json');fs.writeFileSync(file+'.tmp',JSON.stringify({version:1,port:validPort(config.port)},null,2),'utf8');fs.renameSync(file+'.tmp',file);}
function assertPortFree(port){return new Promise((resolve,reject)=>{const s=net.createServer();s.once('error',()=>reject(new Error(`端口 ${port} 已被占用。请退出其他管理程序，或在此修改端口后重试。`)));s.listen(port,'0.0.0.0',()=>s.close(resolve));});}
function copyTree(source,destination){
  const stat=fs.lstatSync(source);
  if(stat.isSymbolicLink())throw new Error('不支持复制链接文件：'+source);
  if(stat.isDirectory()){fs.mkdirSync(destination,{recursive:true});for(const name of fs.readdirSync(source))copyTree(path.join(source,name),path.join(destination,name));}
  else fs.copyFileSync(source,destination);
}
module.exports={validPort,validateSetup,connectionAddresses,readConfig,saveConfig,assertPortFree,copyTree};
