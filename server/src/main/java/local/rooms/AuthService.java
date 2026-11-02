package local.rooms;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static local.rooms.Models.*;

@Service
public class AuthService {
 private final JdbcTemplate db;private final String initialPassword,initialEnrollmentKey;private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);private final Map<String,Long> sessions=new HashMap<>();private final Map<String,Attempt> attempts=new HashMap<>();
 private static final String ENROLLMENT_KEY_NAME="enrollment_key";
 private static final class Attempt {
  final int count;final long reset;
  Attempt(int count,long reset){this.count=count;this.reset=reset;}
  int count(){return count;}long reset(){return reset;}
 }
 public AuthService(JdbcTemplate db,@Value("${room.admin-password}")String password,@Value("${room.enrollment-key}")String key){this.db=db;initialPassword=password;initialEnrollmentKey=key;}
 @PostConstruct void init(){if(initialPassword.length()<8||initialPassword.getBytes(StandardCharsets.UTF_8).length>72||initialEnrollmentKey.length()<12)throw new IllegalStateException("ROOM_ADMIN_PASSWORD至少8字符，ROOM_ENROLLMENT_KEY至少12字符，必须通过环境配置");if(db.queryForObject("select count(*) from administrators",Integer.class)==0)db.update("insert into administrators(username,password_hash) values('admin',?)",encoder.encode(initialPassword));if(db.queryForObject("select count(*) from settings where name=?",Integer.class,ENROLLMENT_KEY_NAME)==0)db.update("insert into settings(name,setting_value) values(?,?)",ENROLLMENT_KEY_NAME,initialEnrollmentKey);}
 public static String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public static String hash(String value){byte[] bytes;try{bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}StringBuilder sb=new StringBuilder(bytes.length*2);for(byte b:bytes)sb.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));return sb.toString();}
 public synchronized String login(Login request,String ip){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->e.getValue().reset()<now);Attempt old=attempts.get(ip);if(old!=null&&old.count()>=10)throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"登录尝试过多，请10分钟后再试");String hash=db.queryForObject("select password_hash from administrators where username='admin'",String.class);if(request==null||!"admin".equals(request.username())||request.password()==null||request.password().getBytes(StandardCharsets.UTF_8).length>72||!encoder.matches(request.password(),hash)){attempts.put(ip,new Attempt(old==null?1:old.count()+1,old==null?now+600_000:old.reset()));throw ApiException.unauthorized();}attempts.remove(ip);sessions.entrySet().removeIf(e->e.getValue()<now);String token=randomToken();sessions.put(hash(token),now+43_200_000);return token;}
 public synchronized void logout(String header){if(header!=null&&header.startsWith("Bearer "))sessions.remove(hash(header.substring(7)));}
 public synchronized void requireAdmin(String header){if(header==null||!header.startsWith("Bearer "))throw ApiException.unauthorized();Long expiry=sessions.get(hash(header.substring(7)));if(expiry==null||expiry<System.currentTimeMillis())throw ApiException.unauthorized();}
 public synchronized void changePassword(PasswordChange request){if(request==null||request.oldPassword()==null||request.oldPassword().getBytes(StandardCharsets.UTF_8).length>72||request.newPassword()==null||request.newPassword().length()<8||request.newPassword().getBytes(StandardCharsets.UTF_8).length>72)throw ApiException.bad("新密码至少8字符且UTF-8长度不超过72字节");String hash=db.queryForObject("select password_hash from administrators where username='admin'",String.class);if(!encoder.matches(request.oldPassword(),hash))throw ApiException.unauthorized();db.update("update administrators set password_hash=? where username='admin'",encoder.encode(request.newPassword()));sessions.clear();}
 public synchronized String enrollmentKey(){return db.queryForObject("select setting_value from settings where name=?",String.class,ENROLLMENT_KEY_NAME);}
 public synchronized void changeEnrollmentKey(String value){if(value==null||value.length()<12||value.length()>255)throw ApiException.bad("注册密钥至少12字符且不超过255字符");db.update("update settings set setting_value=? where name=?",value,ENROLLMENT_KEY_NAME);}
 public void requireEnrollment(String value){String key=enrollmentKey();if(value==null||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),value.getBytes(StandardCharsets.UTF_8)))throw ApiException.unauthorized();}
 public void requireDevice(String id,String token){if(id==null||token==null||token.length()>256)throw ApiException.unauthorized();List<String> hashes=db.query("select token_hash from devices where id=?",(r,n)->r.getString(1),id);if(hashes.isEmpty()||!MessageDigest.isEqual(hashes.get(0).getBytes(StandardCharsets.UTF_8),hash(token).getBytes(StandardCharsets.UTF_8)))throw ApiException.unauthorized();}
}
