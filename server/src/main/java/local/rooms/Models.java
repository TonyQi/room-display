package local.rooms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
public final class Models {
 private Models(){}
 static Map<String,String> json(Object... kv){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i+1<kv.length;i+=2)m.put(String.valueOf(kv[i]),String.valueOf(kv[i+1]));return m;}
 private static boolean eq(Object a,Object b){return a==null?b==null:a.equals(b);}
 public static class RoomState {
  public String id,name,color,icon;
  public RoomState(){}
  public String id(){return id;}public String name(){return name;}public String color(){return color;}public String icon(){return icon;}
  @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof RoomState))return false;RoomState r=(RoomState)o;return eq(id,r.id)&&eq(name,r.name)&&eq(color,r.color)&&eq(icon,r.icon);}
  @Override public int hashCode(){return Objects.hash(id,name,color,icon);}
 }
 public static class Snapshot {
  public String number,name,current;public List<RoomState> states;
  public Snapshot(){}
  public String number(){return number;}public String name(){return name;}public List<RoomState> states(){return states;}public String current(){return current;}
  @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Snapshot))return false;Snapshot s=(Snapshot)o;return eq(number,s.number)&&eq(name,s.name)&&eq(current,s.current)&&eq(states,s.states);}
  @Override public int hashCode(){return Objects.hash(number,name,current,states);}
 }
 public static class Login {
  public String username,password;
  public Login(){}
  public String username(){return username;}public String password(){return password;}
 }
 public static class PasswordChange {
  public String oldPassword,newPassword;
  public PasswordChange(){}
  public String oldPassword(){return oldPassword;}public String newPassword(){return newPassword;}
 }
 public static class Registration {
  public String deviceId,enrollmentKey;
  public Registration(){}
  public String deviceId(){return deviceId;}public String enrollmentKey(){return enrollmentKey;}
 }
 public static class Ack {
  public String id,result,message;
  public Ack(){}
  public String id(){return id;}public String result(){return result;}public String message(){return message;}
 }
 public static class Sync {
  public Long revision;public Snapshot snapshot;public Ack ack;
  public Sync(){}
  public Long revision(){return revision;}public Snapshot snapshot(){return snapshot;}public Ack ack(){return ack;}
 }
 public static class Submit {
  public Long baseRevision;public Snapshot snapshot;
  public Submit(){}
  public Long baseRevision(){return baseRevision;}public Snapshot snapshot(){return snapshot;}
 }
 public static class Template {
  public String id,name;public List<RoomState> states;
  public Template(){}
  public Template(String id,String name,List<RoomState> states){this.id=id;this.name=name;this.states=states;}
  public String id(){return id;}public String name(){return name;}public List<RoomState> states(){return states;}
 }
 public static class Device {
  public String deviceId;public long revision;public Snapshot snapshot;public long lastSeen;public boolean online,pending;
  public Device(){}
  public Device(String deviceId,long revision,Snapshot snapshot,long lastSeen,boolean online,boolean pending){this.deviceId=deviceId;this.revision=revision;this.snapshot=snapshot;this.lastSeen=lastSeen;this.online=online;this.pending=pending;}
  public String deviceId(){return deviceId;}public long revision(){return revision;}public Snapshot snapshot(){return snapshot;}public long lastSeen(){return lastSeen;}public boolean online(){return online;}public boolean pending(){return pending;}
 }
 public static class Command {
  public String id;public long baseRevision,expiresAt;public Snapshot snapshot;
  public Command(){}
  public Command(String id,long baseRevision,long expiresAt,Snapshot snapshot){this.id=id;this.baseRevision=baseRevision;this.expiresAt=expiresAt;this.snapshot=snapshot;}
  public String id(){return id;}public long baseRevision(){return baseRevision;}public long expiresAt(){return expiresAt;}public Snapshot snapshot(){return snapshot;}
 }
 public static class SyncResponse {
  public Command command;
  public SyncResponse(){}
  public SyncResponse(Command command){this.command=command;}
  public Command command(){return command;}
 }
 public static class LogEntry {
  public String id,deviceId,status,message;public long createdAt;public Snapshot snapshot;
  public LogEntry(){}
  public LogEntry(String id,String deviceId,long createdAt,String status,String message,Snapshot snapshot){this.id=id;this.deviceId=deviceId;this.createdAt=createdAt;this.status=status;this.message=message;this.snapshot=snapshot;}
  public String id(){return id;}public String deviceId(){return deviceId;}public long createdAt(){return createdAt;}public String status(){return status;}public String message(){return message;}public Snapshot snapshot(){return snapshot;}
 }
}
