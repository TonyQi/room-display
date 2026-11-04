const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('roomDesktop',Object.freeze({
  info:()=>ipcRenderer.invoke('desktop:info'),
  setup:input=>ipcRenderer.invoke('desktop:setup',input),
  retry:port=>ipcRenderer.invoke('desktop:retry',port),
  openData:()=>ipcRenderer.invoke('desktop:open-data'),
  openLogs:()=>ipcRenderer.invoke('desktop:open-logs'),
  backup:()=>ipcRenderer.invoke('desktop:backup'),
  quit:()=>ipcRenderer.invoke('desktop:quit'),
  onStatus:callback=>{const listener=(_,status)=>callback(status);ipcRenderer.on('desktop:status',listener);return()=>ipcRenderer.removeListener('desktop:status',listener);}
}));
