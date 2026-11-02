let token=sessionStorage.getItem('room-admin-token')||'';
export const getToken=()=>token;
export function setToken(value){token=value;if(value)sessionStorage.setItem('room-admin-token',value);else sessionStorage.removeItem('room-admin-token')}
export async function api(path,{method='GET',body}={}){
 const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),12000);
 try{const response=await fetch('/api'+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body!==undefined?{'Content-Type':'application/json'}:{})},body:body!==undefined?JSON.stringify(body):undefined,signal:controller.signal,cache:'no-store'});
 const text=await response.text();let data;try{data=text?JSON.parse(text):null}catch{throw new Error('服务器返回了无法识别的响应')}
 if(!response.ok){if(response.status===401){setToken('');window.dispatchEvent(new Event('room-auth-expired'))}const error=new Error(data?.message||`请求失败（${response.status}）`);error.status=response.status;throw error}return data;
 }catch(error){if(error.name==='AbortError')throw new Error('请求超时，请检查服务器连接');throw error}finally{clearTimeout(timeout)}
}
