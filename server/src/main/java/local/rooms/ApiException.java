package local.rooms;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
 public final HttpStatus status;
 public ApiException(HttpStatus status,String message){super(message);this.status=status;}
 public static ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,m);}
 public static ApiException conflict(String m){return new ApiException(HttpStatus.CONFLICT,m);}
 public static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"认证失败或登录已过期");}
}
