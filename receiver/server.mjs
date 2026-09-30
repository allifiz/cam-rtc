import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {networkInterfaces} from 'node:os';
import {WebSocketServer, WebSocket} from 'ws';
import {parseJoin, validSignal} from './protocol.mjs';
const port = 8787;
const page = await readFile(new URL('./viewer.html', import.meta.url));
const server = http.createServer((req,res) => {
 if (req.url?.split('?')[0] !== '/') {res.writeHead(404).end(); return;}
 res.writeHead(200, {'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer'}).end(page);
});
const wss = new WebSocketServer({noServer:true,maxPayload:110000});
const peers = new Map();
const send = (ws,msg) => {if(ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));};
server.on('upgrade',(req,socket,head) => {
 const role = parseJoin(req.url);
 if (!role || peers.has(role)) {socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');socket.destroy();return;}
 wss.handleUpgrade(req,socket,head,ws => {
  peers.set(role,ws);
  const otherRole = role === 'sender' ? 'viewer' : 'sender';
  if(peers.has(otherRole)) {
   send(peers.get('sender'),{type:'ready'});
   send(peers.get('viewer'),{type:'ready'});
  }
  let windowStart = Date.now(), messages = 0;
  ws.on('message',raw => {
   if(Date.now()-windowStart > 1000) {windowStart=Date.now();messages=0;}
   if(++messages > 100) {ws.close(1008,'Rate limit');return;}
   try {
    const msg = JSON.parse(raw.toString());
    if(!validSignal(msg) || role === 'sender' && msg.type === 'answer' || role === 'viewer' && msg.type === 'offer') {ws.close(1008,'Invalid signal');return;}
    send(peers.get(otherRole),msg);
   } catch {ws.close(1008,'Invalid JSON');}
  });
  ws.on('error',()=>{});
  ws.on('close',() => {
   if(peers.get(role) === ws) peers.delete(role);
   send(peers.get(otherRole),{type:'peer-left'});
  });
 });
});
server.listen(port,'0.0.0.0',() => {
 for(const rows of Object.values(networkInterfaces())) for(const ip of rows ?? [])
  if(ip.family === 'IPv4' && !ip.internal) console.log('Android PC address: '+ip.address);
 console.log('OBS URL: http://127.0.0.1:'+port+'/');
});
