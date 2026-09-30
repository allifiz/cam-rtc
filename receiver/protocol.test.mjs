import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseJoin, validSignal} from './protocol.mjs';
test('accepts token-free clients and rejects invalid roles/paths', () => {
 assert.equal(parseJoin('/signal?role=sender'),'sender');
 assert.equal(parseJoin('/signal?role=viewer'),'viewer');
 assert.equal(parseJoin('/signal?role=viewer&token=legacy'),'viewer');
 for (const u of ['/signal?role=admin','/bad?role=viewer','/signal']) assert.equal(parseJoin(u),null);
});
test('rejects malformed signaling', () => {
 assert.ok(validSignal({type:'offer',sdp:'v=0'}));
 assert.ok(validSignal({type:'ice',candidate:'candidate:1',sdpMid:'0',sdpMLineIndex:0}));
 for (const v of [null,{}, {type:'offer',sdp:4},{type:'ice',candidate:'x',sdpMid:'0',sdpMLineIndex:-1}]) assert.ok(!validSignal(v));
});
