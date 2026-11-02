import json,time,urllib.request,urllib.error,pathlib,threading,concurrent.futures,uuid,os
ROOT=pathlib.Path(__file__).resolve().parent
config={'username':'admin','password':os.environ['ROOM_ADMIN_PASSWORD'],'enrollmentKey':os.environ['ROOM_ENROLLMENT_KEY'],'url':os.environ.get('ROOM_TEST_URL','http://localhost:8080')};url=config['url']
def api(path,body=None,token=None,device=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if device:h.update({'X-Device-Id':device['id'],'X-Device-Token':device['token']})
 req=urllib.request.Request(url+path,data=json.dumps(body).encode() if body is not None else None,headers=h)
 try:
  with urllib.request.urlopen(req,timeout=10) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def snap(n):return {'number':str(n),'name':'联调房间'+str(n),'states':[{'id':'free','name':'空闲','color':'#83DFC2','icon':'✓'},{'id':'busy','name':'使用中','color':'#FFC18E','icon':'◷'}],'current':'free'}
def main():
 assert api('/api/admin/devices')[0]==401
 code,a=api('/api/auth/login',{'username':config['username'],'password':config['password']});assert code==200;token=a['token']
 assert api('/api/device/register',{'deviceId':'bad','enrollmentKey':'invalid'})[0]==401
 devices=[]
 for i in range(30):
  id='integration-'+uuid.uuid4().hex[:10];code,r=api('/api/device/register',{'deviceId':id,'enrollmentKey':config['enrollmentKey']});assert code==200
  d={'id':id,'token':r['token'],'revision':0,'snapshot':snap(500+i)};devices.append(d)
 def sync(d):return api('/api/device/sync',{'revision':d['revision'],'snapshot':d['snapshot']},device=d)
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:assert all(r[0]==200 for r in pool.map(sync,devices))
 d=devices[0];desired=snap(500);desired['current']='busy'
 code,c=api('/api/admin/devices/'+d['id']+'/commands',{'baseRevision':0,'snapshot':desired},token);assert code==200
 code,r=sync(d);assert r['command']['id']==c['id']
 d['revision']=1;d['snapshot']=desired
 ack={'id':c['id'],'result':'APPLIED','message':'protocol test saved'}
 body={'revision':1,'snapshot':desired,'ack':ack}
 assert api('/api/device/sync',body,device=d)[0]==200
 assert api('/api/device/sync',body,device=d)[0]==200
 assert api('/api/admin/devices/'+d['id']+'/commands',{'baseRevision':0,'snapshot':desired},token)[0]==409
 code,c2=api('/api/admin/devices/'+d['id']+'/commands',{'baseRevision':1,'snapshot':snap(500)},token);assert code==200
 d['revision']=2;d['snapshot']={**desired,'name':'本地离线编辑优先'}
 assert sync(d)[1]['command'] is None
 logs=api('/api/admin/logs',token=token)[1];assert next(l for l in logs if l['id']==c2['id'])['status']=='CONFLICT'
 assert api('/api/device/sync',{'revision':1,'snapshot':desired},device=d)[0]==409
 assert api('/api/device/sync',{'revision':2,'snapshot':snap(999)},device=d)[0]==409
 (ROOT/'integration-devices.json').write_text(json.dumps(devices,ensure_ascii=False),encoding='utf-8')
 print('PASS auth, 30 concurrent device snapshots, real command/ack, duplicate ack, local conflict, stale revision and content mismatch',flush=True)
 time.sleep(16)
 assert api('/api/admin/devices/'+d['id']+'/commands',{'baseRevision':2,'snapshot':desired},token)[0]==409
 assert sync(d)[0]==200
 print('PASS offline rejection and reconnect local snapshot',flush=True)
 (ROOT/'integration-result.json').write_text(json.dumps({'passed':True,'devices':30,'time':time.time()}))
if __name__=='__main__':main()
