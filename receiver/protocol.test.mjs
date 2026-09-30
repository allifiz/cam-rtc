import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseJoin, validSignal} from './protocol.mjs';
test('requires token and valid role/path', () => {
 assert.equal(parseJoin('/signal?role=sender&token=abc','abc'),'sender');
 for (const u of ['/signal?role=sender&token=bad','/signal?role=admin&token=abc','/bad?role=viewer&token=abc']) assert.equal(parseJoin(u,'abc'),null);
});
test('rejects malformed signaling', () => {
 assert.ok(validSignal({type:'offer',sdp:'v=0'}));
 assert.ok(validSignal({type:'ice',candidate:'candidate:1',sdpMid:'0',sdpMLineIndex:0}));
 for (const v of [null,{}, {type:'offer',sdp:4},{type:'ice',candidate:'x',sdpMid:'0',sdpMLineIndex:-1}]) assert.ok(!validSignal(v));
});
