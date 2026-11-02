package local.rooms;

import static local.rooms.Models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class RoomService {
 private final JdbcTemplate db;private final ObjectMapper json;private final TransactionTemplate tx;private final AuthService auth;
 private static final class StoredCommand {
  final String id,deviceId,status;final long baseRevision,expiresAt;final Snapshot snapshot;
  StoredCommand(String id,String deviceId,long baseRevision,Snapshot snapshot,String status,long expiresAt){this.id=id;this.deviceId=deviceId;this.baseRevision=baseRevision;this.snapshot=snapshot;this.status=status;this.expiresAt=expiresAt;}
  String id(){return id;}String deviceId(){return deviceId;}long baseRevision(){return baseRevision;}Snapshot snapshot(){return snapshot;}String status(){return status;}long expiresAt(){return expiresAt;}
 }
 public RoomService(JdbcTemplate db,ObjectMapper json,TransactionTemplate tx,AuthService auth){this.db=db;this.json=json;this.tx=tx;this.auth=auth;}
 @PostConstruct void restart(){db.update("update commands set status='EXPIRED',message='服务器重启，指令失效' where status='PENDING'");db.update("update devices set last_seen=0");}
 String encode(Object o){try{return json.writeValueAsString(o);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
 <T>T decode(String s,Class<T> type){if(s==null)return null;try{return json.readValue(s,type);}catch(JsonProcessingException e){throw new IllegalStateException("数据库JSON损坏",e);}}
 private Snapshot snapshot(ResultSet rs)throws SQLException{return decode(rs.getString("snapshot"),Snapshot.class);}
 private StoredCommand command(ResultSet rs,int n)throws SQLException{return new StoredCommand(rs.getString("id"),rs.getString("device_id"),rs.getLong("base_revision"),snapshot(rs),rs.getString("status"),rs.getLong("expires_at"));}
 private void expire(long now){db.update("update commands set status='EXPIRED',message='设备确认超时，指令失效' where status='PENDING' and expires_at<?",now);}
 private List<StoredCommand> pending(String id){return db.query("select * from commands where device_id=? and status='PENDING'",this::command,id);}
 public synchronized Map<String,String> register(Registration r){if(r==null)throw ApiException.bad("缺少注册信息");auth.requireEnrollment(r.enrollmentKey());Validation.text(r.deviceId(),80,"设备编号");if(!r.deviceId().matches("[A-Za-z0-9_-]+"))throw ApiException.bad("设备编号仅允许字母、数字、下划线及连字符");if(db.queryForObject("select count(*) from devices where id=?",Integer.class,r.deviceId())!=0)throw ApiException.conflict("设备编号已注册；不得覆盖原设备凭据");String token=AuthService.randomToken();db.update("insert into devices(id,token_hash,revision,last_seen) values(?,?,-1,0)",r.deviceId(),AuthService.hash(token));return json("token",token);}
 public synchronized List<Device> devices(){long now=System.currentTimeMillis();expire(now);return db.query("select * from devices order by id",(r,n)->new Device(r.getString("id"),r.getLong("revision"),snapshot(r),r.getLong("last_seen"),now-r.getLong("last_seen")<=15000,!pending(r.getString("id")).isEmpty()));}
 public synchronized SyncResponse sync(String id,Sync request){if(request==null||request.revision()==null)throw ApiException.bad("缺少版本号");if(request.revision()<0)throw ApiException.conflict("设备版本号不能为负数");Validation.snapshot(request.snapshot());return tx.execute(status->{long now=System.currentTimeMillis();Map<String,Object> d=db.queryForMap("select revision,snapshot from devices where id=?",id);long previous=((Number)d.get("REVISION")).longValue();Snapshot old=db.queryForObject("select snapshot from devices where id=?",(r,n)->decode(r.getString(1),Snapshot.class),id);if(request.revision()<previous)throw ApiException.conflict("设备版本落后，拒绝回滚；请检查设备数据是否被恢复或复制");if(request.revision()==previous&&old!=null&&!old.equals(request.snapshot()))throw ApiException.conflict("相同版本的房间数据不一致");
   Ack ack=request.ack();if(ack!=null){Validation.text(ack.id(),80,"确认指令编号");if(!Arrays.asList("APPLIED","CONFLICT","FAILED").contains(ack.result()==null?"":ack.result()))throw ApiException.bad("无效确认结果");List<StoredCommand> cs=db.query("select * from commands where id=? and device_id=?",this::command,ack.id(),id);if(cs.isEmpty())throw ApiException.bad("确认的指令不存在或不属于当前设备");StoredCommand c=cs.get(0);if("APPLIED".equals(ack.result())){if(request.revision()<=c.baseRevision())throw ApiException.conflict("成功确认必须增加设备版本号");if(request.revision()==c.baseRevision()+1&&!request.snapshot().equals(c.snapshot()))throw ApiException.conflict("成功确认与指令数据不一致");}
   if("PENDING".equals(c.status())||("EXPIRED".equals(c.status())&&"APPLIED".equals(ack.result()))){String message=ack.message()==null?"":ack.message();if(message.length()>500)message=message.substring(0,500);db.update("update commands set status=?,message=? where id=?",ack.result(),message,c.id());}}
   db.update("update devices set revision=?,snapshot=?,last_seen=? where id=?",request.revision(),encode(request.snapshot()),now,id);expire(now);List<StoredCommand> cs=pending(id);if(cs.isEmpty())return new SyncResponse(null);StoredCommand c=cs.get(0);if(c.baseRevision()!=request.revision()){db.update("update commands set status='CONFLICT',message='设备已在本地更新，请刷新数据后重新操作' where id=?",c.id());return new SyncResponse(null);}return new SyncResponse(new Command(c.id(),c.baseRevision(),c.expiresAt(),c.snapshot()));});}
 public synchronized Map<String,String> submit(String id,Submit request){if(request==null||request.baseRevision()==null)throw ApiException.bad("缺少基础版本号");Validation.snapshot(request.snapshot());return tx.execute(status->{long now=System.currentTimeMillis();expire(now);List<Device> ds=db.query("select * from devices where id=?",(r,n)->new Device(id,r.getLong("revision"),snapshot(r),r.getLong("last_seen"),now-r.getLong("last_seen")<=15000,false),id);if(ds.isEmpty())throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"设备不存在");Device d=ds.get(0);if(!d.online()||d.snapshot()==null)throw ApiException.conflict("设备离线或尚未同步，不能下发指令");if(d.revision()!=request.baseRevision())throw ApiException.conflict("数据已变化，请刷新后重试");if(!pending(id).isEmpty())throw ApiException.conflict("已有指令等待设备确认");String cid=UUID.randomUUID().toString();db.update("insert into commands(id,device_id,base_revision,snapshot,status,message,created_at,expires_at) values(?,?,?,?,'PENDING','等待设备确认',?,?)",cid,id,d.revision(),encode(request.snapshot()),now,now+15000);return json("id",cid,"status","PENDING");});}
 public synchronized List<LogEntry> logs(){expire(System.currentTimeMillis());return db.query("select * from commands order by created_at desc limit 1000",(r,n)->new LogEntry(r.getString("id"),r.getString("device_id"),r.getLong("created_at"),r.getString("status"),r.getString("message"),snapshot(r)));}
 public synchronized List<Template> templates(){return db.query("select * from templates order by name",(r,n)->new Template(r.getString("id"),r.getString("name"),Arrays.asList(decode(r.getString("states"),RoomState[].class))));}
 public synchronized Template saveTemplate(Template t){if(t==null)throw ApiException.bad("缺少模板");Validation.text(t.name(),80,"模板名称");Validation.states(t.states());String id=t.id()==null||t.id().trim().isEmpty()?UUID.randomUUID().toString():t.id();Validation.text(id,80,"模板编号");if(!id.matches("[A-Za-z0-9_-]+"))throw ApiException.bad("模板编号格式不正确");db.update("merge into templates(id,name,states) key(id) values(?,?,?)",id,t.name().trim(),encode(t.states()));return new Template(id,t.name().trim(),t.states());}
 public synchronized void deleteTemplate(String id){if(db.update("delete from templates where id=?",id)==0)throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"模板不存在");}
}
