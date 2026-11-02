// Browser interaction tests use an intercepted API fixture, never production data.
// Run a Vite dev server, then set PLAYWRIGHT_MODULE to a Playwright package path.
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const browser=await chromium.launch({channel:'chrome',headless:true});
const page=await browser.newPage({viewport:{width:1536,height:1050}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
const states=[{id:'free',name:'空闲',color:'#83dfc2',icon:'✓'},{id:'busy',name:'使用中',color:'#ffc18e',icon:'◷'}];
let devices=Array.from({length:25},(_,i)=>({deviceId:'test-'+i,revision:2,snapshot:i===24?null:{number:String(301+i),name:'会议室 '+i,states:structuredClone(states),current:'free'},online:i%5!==4&&i!==24,pending:false,lastSeen:Date.now()}));
let templates=[],logs=[],commands=[];
await page.route('**/api/**',async route=>{const request=route.request(),url=new URL(request.url()),path=url.pathname,body=request.postDataJSON();let result={};
 if(path==='/api/auth/login')result={token:'test-token'};
 else if(path==='/api/auth/me')result={username:'admin'};
 else if(path==='/api/admin/devices')result=devices;
 else if(path==='/api/admin/logs')result=logs;
 else if(path==='/api/admin/templates'&&request.method()==='GET')result=templates;
 else if(path==='/api/admin/templates'&&request.method()==='POST'){const t={...body,id:body.id||'template-1'};templates=templates.filter(x=>x.id!==t.id).concat(t);result=t}
 else if(path.startsWith('/api/admin/templates/')&&request.method()==='DELETE')templates=[];
 else if(path.endsWith('/commands')){commands.push(body);const d=devices.find(d=>path.includes(d.deviceId+'/'));d.pending=true;result={id:'command-1',status:'PENDING'};logs=[{id:'command-1',deviceId:d.deviceId,createdAt:Date.now(),snapshot:body.snapshot,status:'PENDING'}]}
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(result)});
});
try{
 await page.goto(process.env.WEB_TEST_URL||'http://127.0.0.1:5173');
 await page.locator('[name=password]').fill('test-password');await page.getByRole('button',{name:'登录管理中心'}).click();
 await page.locator('.card').first().waitFor();assert.equal(await page.locator('.card').count(),12);
 await page.getByRole('button',{name:'下一页'}).click();assert.match(await page.locator('.card').first().textContent(),/test-12/);
 await page.getByLabel('搜索设备').fill('test-0');await page.getByRole('button',{name:'切换状态',exact:true}).click();
 await page.locator('.choices button').filter({hasText:'使用中'}).click();await page.waitForTimeout(200);assert.equal(commands[0].baseRevision,2);assert.equal(commands[0].snapshot.current,'busy');assert.match(await page.locator('.card').textContent(),/等待确认/);
 devices[0].pending=false;devices[0].snapshot.current='busy';devices[0].revision=3;
 await page.getByRole('button',{name:'状态模板',exact:false}).first().click();await page.getByRole('button',{name:'＋ 新建模板'}).click();
 await page.locator('.modal-box').getByLabel('模板名称').fill('标准模板');await page.getByLabel('状态名称').fill('开放');await page.getByRole('button',{name:'保存模板',exact:true}).click();await page.locator('.template').waitFor();assert.equal(templates[0].states[0].name,'开放');
 await page.getByRole('button',{name:'选择设备并下发'}).click();assert.equal(await page.locator('.checklist input:disabled').count(),5);await page.locator('.checklist input:not(:disabled)').first().check();await page.getByRole('button',{name:'确认下发至 1 台设备'}).click();await page.locator('.batch-result').waitFor();assert.equal(commands[1].snapshot.current,templates[0].states[0].id);await page.getByRole('button',{name:'完成',exact:true}).click();
 await page.getByRole('button',{name:'设备管理',exact:false}).click();await page.getByLabel('搜索设备').fill('test-24');assert.match(await page.locator('tbody').textContent(),/等待同步/);
 await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert.deepEqual(errors,[]);console.log('PASS browser: login, pagination, search, pending commands, template CRUD/create, offline exclusion, batch snapshot, null snapshot, narrow viewport.');
}finally{await browser.close()}

