package local.rooms;

import static local.rooms.Models.*;
import java.util.*;
final class Validation {
 static String text(String s,int max,String field){if(s==null||s.trim().isEmpty()||s.length()>max)throw ApiException.bad(field+"不能为空且长度不能超过"+max);return s;}
 static void states(List<RoomState> states){if(states==null||states.isEmpty()||states.size()>30)throw ApiException.bad("状态数量须为1至30个");Set<String> ids=new HashSet<>(),names=new HashSet<>();for(RoomState s:states){if(s==null)throw ApiException.bad("状态不能为null");text(s.id(),80,"状态编号");text(s.name(),16,"状态名称");text(s.icon(),16,"图标");if(!s.id().matches("[A-Za-z0-9_-]+")||!ids.add(s.id())||!names.add(s.name().trim()))throw ApiException.bad("状态编号格式错误或名称重复");if(s.color()==null||!s.color().matches("#[0-9a-fA-F]{6}"))throw ApiException.bad("状态颜色须为#RRGGBB");}}
 static void snapshot(Snapshot s){if(s==null)throw ApiException.bad("缺少房间数据");text(s.number(),24,"房间号");text(s.name(),80,"房间名称");states(s.states());if(s.states().stream().noneMatch(x->x.id().equals(s.current())))throw ApiException.bad("当前状态不存在");}
}
