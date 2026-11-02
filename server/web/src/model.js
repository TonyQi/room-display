export function filterDevices(devices,query='',filter='all') { return devices.filter(d=>`${d.deviceId} ${d.snapshot?.number||''} ${d.snapshot?.name||''}`.toLowerCase().includes(query.trim().toLowerCase())&&(filter==='all'||(filter==='online'?d.online:!d.online))); }
export function validateStates(states) { if(!states.length||states.length>32)return '状态数量应为1–32个';const ids=new Set(),names=new Set();for(const s of states){if(!s.id||!s.name?.trim()||s.name.trim().length>16||!s.icon?.trim()||!/^#[0-9a-f]{6}$/i.test(s.color))return '请填写有效的状态名称、图标和颜色';if(ids.has(s.id)||names.has(s.name.trim()))return '状态标识及名称不能重复';ids.add(s.id);names.add(s.name.trim())}return ''; }
export function templateSnapshot(snapshot,states,current) { if(!states.some(s=>s.id===current))throw new Error('请选择模板中的当前状态');return {...snapshot,states:structuredClone(states),current}; }
export const statusLabels={PENDING:'等待确认',APPLIED:'已生效',CONFLICT:'版本冲突',FAILED:'执行失败',EXPIRED:'指令已过期'};
export const formatTime=value=>value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'尚未同步';
export const currentState=d=>d.snapshot?.states?.find(s=>s.id===d.snapshot.current)||{name:'尚无状态',color:'#a4b5c8',icon:'○'};
