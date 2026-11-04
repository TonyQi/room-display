const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { validateSetup, connectionAddresses, readConfig, saveConfig, assertPortFree } = require('../app/core.cjs');

test('first-run passwords and ports must be valid', () => {
  assert.throws(() => validateSetup({password:'short',confirm:'short',port:8080}));
  assert.throws(() => validateSetup({password:'Password123!',confirm:'different',port:8080}));
  assert.throws(() => validateSetup({password:'Password123!',confirm:'Password123!',port:80}));
  assert.deepEqual(validateSetup({password:'Password123!',confirm:'Password123!',port:'8080'}),{password:'Password123!',port:8080});
});
test('LAN addresses skip loopback, IPv6 and link-local, deduplicate', () => {
  assert.deepEqual(connectionAddresses(8080,{a:[{family:'IPv4',address:'127.0.0.1',internal:true},{family:'IPv4',address:'192.168.1.10',internal:false}],b:[{family:'IPv4',address:'192.168.1.10',internal:false},{family:'IPv4',address:'169.254.1.2',internal:false},{family:'IPv6',address:'::1',internal:true}]}),['http://192.168.1.10:8080']);
});
test('config survives restart, stores no passwords, invalid config never silently resets', () => {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'room-desktop-test-'));
  try {
    assert.equal(readConfig(dir),null);
    saveConfig(dir,{port:8123,password:'must-not-be-persisted'});
    assert.deepEqual(readConfig(dir),{version:1,port:8123});
    assert(!fs.readFileSync(path.join(dir,'config.json'),'utf8').includes('password'));
    fs.writeFileSync(path.join(dir,'config.json'),'{bad');
    assert.throws(()=>readConfig(dir));
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});
test('occupied port is refused, released port works', async () => {
  const server=net.createServer(); await new Promise(r=>server.listen(0,'0.0.0.0',r));
  const port=server.address().port;
  await assert.rejects(assertPortFree(port),/占用/);
  await new Promise(r=>server.close(r));
  await assertPortFree(port);
});
