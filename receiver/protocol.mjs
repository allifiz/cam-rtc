export function parseJoin(url) {
  const u = new URL(url, 'http://localhost');
  const role = u.searchParams.get('role');
  return u.pathname === '/signal' &&
    ['sender', 'viewer'].includes(role) ? role : null;
}
export function validSignal(value) {
  return value && (
    ['offer', 'answer'].includes(value.type) && typeof value.sdp === 'string' && value.sdp.length < 100000 ||
    value.type === 'ice' && typeof value.candidate === 'string' && value.candidate.length < 4096 &&
      typeof value.sdpMid === 'string' && Number.isInteger(value.sdpMLineIndex) && value.sdpMLineIndex >= 0
  );
}
