package local.rooms;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;
import static local.rooms.Models.*;

@RestController @RequestMapping("/api")
public class ApiController {
 private final AuthService auth;private final RoomService rooms;
 public ApiController(AuthService auth,RoomService rooms){this.auth=auth;this.rooms=rooms;}
 @GetMapping("/health") Map<String,String> health(){Map<String,String> result=json("status","UP");String instance=System.getenv("ROOM_INSTANCE_TOKEN");if(instance!=null)result.put("instance",instance);return result;}
 @PostMapping("/auth/login") Map<String,String> login(@RequestBody Login login,HttpServletRequest request){return json("token",auth.login(login,request.getRemoteAddr()));}
 @GetMapping("/auth/me") Map<String,String> me(){return json("username","admin");}
 @PostMapping("/auth/password") Map<String,String> password(@RequestBody PasswordChange request){auth.changePassword(request);return json("message","密码已修改，请重新登录");}
 @PostMapping("/auth/logout") Map<String,String> logout(@RequestHeader("Authorization")String header){auth.logout(header);return json("message","已退出登录");}
 @PostMapping("/device/register") Map<String,String> register(@RequestBody Registration registration){return rooms.register(registration);}
 @PostMapping("/device/sync") SyncResponse sync(@RequestHeader("X-Device-Id")String id,@RequestBody Sync request){return rooms.sync(id,request);}
 @GetMapping("/admin/devices") List<Device> devices(){return rooms.devices();}
 @PostMapping("/admin/devices/{id}/commands") Map<String,String> submit(@PathVariable String id,@RequestBody Submit request){return rooms.submit(id,request);}
 @GetMapping("/admin/logs") List<LogEntry> logs(){return rooms.logs();}
 @GetMapping("/admin/templates") List<Template> templates(){return rooms.templates();}
 @PostMapping("/admin/templates") Template save(@RequestBody Template template){return rooms.saveTemplate(template);}
 @DeleteMapping("/admin/templates/{id}") ResponseEntity<Void> delete(@PathVariable String id){rooms.deleteTemplate(id);return ResponseEntity.noContent().build();}
 @GetMapping("/admin/settings/enrollment-key") Map<String,String> enrollmentKey(){return json("enrollmentKey",auth.enrollmentKey());}
 @PostMapping("/admin/settings/enrollment-key") Map<String,String> changeEnrollmentKey(@RequestBody Map<String,String> request){auth.changeEnrollmentKey(request==null?null:request.get("enrollmentKey"));return json("message","注册密钥已更新，之后注册的设备请使用新密钥");}
}
@Configuration
class ApiAccess implements WebMvcConfigurer {
 private final AuthService auth;ApiAccess(AuthService auth){this.auth=auth;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){@Override public boolean preHandle(HttpServletRequest req,HttpServletResponse response,Object handler){response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("X-Frame-Options","DENY");response.setHeader("Cache-Control","no-store");String uri=req.getRequestURI();if(uri.startsWith("/api/admin/")||Arrays.asList("/api/auth/me","/api/auth/password","/api/auth/logout").contains(uri))auth.requireAdmin(req.getHeader("Authorization"));if(uri.equals("/api/device/sync"))auth.requireDevice(req.getHeader("X-Device-Id"),req.getHeader("X-Device-Token"));return true;}}).addPathPatterns("/api/**");}
}
@RestControllerAdvice
class Errors {
 @ExceptionHandler(ApiException.class)ResponseEntity<Map<String,String>> api(ApiException e){return ResponseEntity.status(e.status).body(json("message",e.getMessage()));}
 @ExceptionHandler({HttpMessageNotReadableException.class,org.springframework.web.bind.MissingRequestHeaderException.class})ResponseEntity<Map<String,String>> invalid(Exception e){return ResponseEntity.badRequest().body(json("message","请求格式不正确或缺少必要字段"));}
 @ExceptionHandler(Exception.class)ResponseEntity<Map<String,String>> other(Exception e){org.slf4j.LoggerFactory.getLogger(Errors.class).error("API processing failed",e);return ResponseEntity.status(500).body(json("message","服务器处理失败，请检查服务器日志"));}
}
