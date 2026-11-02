import test from 'node:test';
import assert from 'node:assert/strict';
import { filterDevices, templateSnapshot, validateStates } from '../src/model.js';
test('search supports missing snapshot and connection filter',()=>{const ds=[{deviceId:'x',online:true,snapshot:{number:'301',name:'会议室'}},{deviceId:'y',online:false,snapshot:null}];assert.equal(filterDevices(ds,'会议','online').length,1);assert.equal(filterDevices(ds,'','offline').length,1)});
test('template replacement leaves original device snapshot untouched',()=>{const d={number:'301',name:'会议室',states:[{id:'old'}],current:'old'};const states=[{id:'new',name:'空闲',color:'#83dfc2',icon:'✓'}];const s=templateSnapshot(d,states,'new');assert.equal(s.current,'new');assert.equal(d.current,'old');s.states[0].name='changed';assert.equal(states[0].name,'空闲')});
test('invalid template cannot target nonexistent state',()=>assert.throws(()=>templateSnapshot({},[{id:'a'}],'b')));
test('state validation rejects blanks, duplicate IDs/names and invalid color',()=>{assert(validateStates([{id:'a',name:' ',color:'#ffffff',icon:'✓'}]));assert(validateStates([{id:'a',name:'A',color:'red',icon:'✓'}]));assert.equal(validateStates([{id:'a',name:'A',color:'#123abc',icon:'✓'}]),'')});
