import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const browser=await chromium.launch({channel:'chrome',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
page.on('pageerror',e=>errors.push(e.message));
await page.addInitScript(()=>{window.calls={open:0,backup:0};window.roomDesktop={info:async()=>({addresses:['192.168.1.22','http://10.0.0.2:8080'],port:8080,dataDir:'D:\\RoomDisplay\\data',version:'1.0.0'}),openData:async()=>window.calls.open++,backup:async()=>{window.calls.backup++;return {cancelled:false,path:'D:\\backup\\room-backup.zip'}}}});
await page.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;let result={};
if(path==='/api/auth/login')result={token:'test'};else if(path==='/api/auth/me')result={username:'admin'};else if(path==='/api/admin/settings/enrollment-key')result={enrollmentKey:'fixture-key-never-production'};else if(['/api/admin/devices','/api/admin/templates','/api/admin/logs'].includes(path))result=[];
await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(result)});
});
try{
await page.goto(process.env.WEB_TEST_URL||'http://127.0.0.1:5176');
assert.match(await page.locator('.login-card').textContent(),/首次启动管理程序/);
assert.doesNotMatch(await page.locator('.login-card').textContent(),/fixture-key/);
await page.locator('[name=password]').fill('fixture-password');await page.getByRole('button',{name:'登录管理中心'}).click();
await page.getByRole('button',{name:'＋ 接入设备'}).click();
assert.match(await page.locator('.modal-box').textContent(),/http:\/\/192.168.1.22:8080/);assert.match(await page.locator('.modal-box').textContent(),/http:\/\/10.0.0.2:8080/);assert.match(await page.locator('.modal-box').textContent(),/原有“服务器地址”/);
await page.getByRole('button',{name:'关闭弹窗'}).click();await page.getByRole('button',{name:'系统设置',exact:false}).click();
await page.getByRole('button',{name:'打开数据目录'}).click();assert.equal(await page.evaluate(()=>window.calls.open),1);
await page.getByRole('button',{name:'备份数据',exact:true}).click();await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(await page.evaluate(()=>window.calls.backup),0);
await page.getByRole('button',{name:'备份数据',exact:true}).click();await page.getByRole('button',{name:'选择位置并备份'}).click();await page.locator('.login-card').waitFor();assert.match(await page.locator('.login-card .error').textContent(),/备份已完成/);assert.equal(await page.evaluate(()=>window.calls.backup),1);
assert.deepEqual(errors,[]);console.log('PASS desktop bridge: real address display, no login key leakage, open folder, backup confirmation and re-login.');
}finally{await browser.close()}
