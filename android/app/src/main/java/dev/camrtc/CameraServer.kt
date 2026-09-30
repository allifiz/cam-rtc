package dev.camrtc

import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*

class CameraServer(private val host: String, private val width: Int, private val height: Int, private val fps: Int,
 private val report: (String) -> Unit, private val keyFrame: () -> Unit) : AutoCloseable {
 private val pool = Executors.newCachedThreadPool()
 private val clients = CopyOnWriteArrayList<RtspClient>()
 private val sockets = CopyOnWriteArrayList<Socket>()
 private val listeners = mutableListOf<ServerSocket>()
 private var discovery: MulticastSocket? = null
 @Volatile private var active = true
 @Volatile private var sps = byteArrayOf()
 @Volatile private var pps = byteArrayOf()
 private val instance = System.currentTimeMillis()/1000
 private val messageNumber = java.util.concurrent.atomic.AtomicInteger()
 private val uuid = "urn:uuid:" + UUID.nameUUIDFromBytes("CamRTC-$host".toByteArray())
 private val base get() = "http://$host:8080/onvif"
 fun start() {
  try {
   for(port in listOf(8080,8554)) {
    val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }; listeners.add(listener)
    pool.execute { while(active) try {
     val socket = listener.accept(); socket.reuseAddress = true; socket.soTimeout = 15000; sockets.add(socket)
     pool.execute { try { if(port == 8080) http(socket) else rtsp(socket) } catch(_: Exception) {} finally { sockets.remove(socket); socket.close() } }
    } catch(_: Exception) {} }
   }
   val multicast = MulticastSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(3702)); networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(host)); joinGroup(InetSocketAddress("239.255.255.250",3702), networkInterface) }
   discovery = multicast
   pool.execute {
    sendDiscovery("Hello", null, InetAddress.getByName("239.255.255.250"),3702)
    val bytes = ByteArray(65507)
    while(active) try {
     val packet = DatagramPacket(bytes,bytes.size); multicast.receive(packet)
     val xml = String(packet.data,0,packet.length, Charsets.UTF_8)
     if(Regex("<(?:\\w+:)?Probe(?:\\s|/?>)").containsMatchIn(xml)) {
      val id = Regex("<(?:\\w+:)?MessageID[^>]*>([^<]+)").find(xml)?.groupValues?.get(1)
      if(id != null && Regex("[a-zA-Z0-9:._-]{1,200}").matches(id)) sendDiscovery("ProbeMatches", id, packet.address, packet.port)
     }
    } catch(_: Exception) {}
   }
  } catch(e: Exception) { close(); throw e }
 }
 private fun sendDiscovery(action: String, relates: String?, address: InetAddress, port: Int) {
  val endpoint = "<a:EndpointReference><a:Address>$uuid</a:Address></a:EndpointReference><d:Types>dn:NetworkVideoTransmitter tds:Device</d:Types><d:Scopes>onvif://www.onvif.org/type/video_encoder onvif://www.onvif.org/name/Cam%20RTC onvif://www.onvif.org/hardware/Android onvif://www.onvif.org/Profile/Streaming</d:Scopes><d:XAddrs>$base/device_service</d:XAddrs><d:MetadataVersion>1</d:MetadataVersion>"
  val body = if(action == "ProbeMatches") "<d:ProbeMatches><d:ProbeMatch>$endpoint</d:ProbeMatch></d:ProbeMatches>" else "<d:Hello>$endpoint</d:Hello>"
  val xml = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl" xmlns:tds="http://www.onvif.org/ver10/device/wsdl"><s:Header><a:MessageID>urn:uuid:${UUID.randomUUID()}</a:MessageID>${if(relates != null) "<a:RelatesTo>$relates</a:RelatesTo>" else ""}<a:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:To><a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/$action</a:Action><d:AppSequence InstanceId="$instance" MessageNumber="${messageNumber.incrementAndGet()}"/></s:Header><s:Body>$body</s:Body></s:Envelope>"""
  val bytes = xml.toByteArray(); discovery?.send(DatagramPacket(bytes,bytes.size,address,port))
 }
 private fun line(input: InputStream): String {
  val bytes = ByteArrayOutputStream()
  while(bytes.size() < 8192) { val b = input.read(); if(b < 0) throw EOFException(); if(b == 10) return bytes.toString("UTF-8").trimEnd('\r'); bytes.write(b) }
  error("Header too large")
 }
 private fun headers(input: InputStream): Map<String,String> {
  val result = mutableMapOf<String,String>()
  repeat(100) { val value = line(input); if(value.isEmpty()) return result; val colon = value.indexOf(':'); if(colon > 0) result[value.substring(0,colon).lowercase(Locale.ROOT)] = value.substring(colon+1).trim() }
  error("Too many headers")
 }
 private fun http(socket: Socket) {
  val input = socket.getInputStream(); val first = line(input); val headers = headers(input)
  val length = headers["content-length"]?.toIntOrNull() ?: 0; require(length in 0..65536)
  val bytes = ByteArray(length); DataInputStream(input).readFully(bytes)
  val request = String(bytes, Charsets.UTF_8)
  val operation = Regex("<(?:\\w+:)?Body[^>]*>\\s*<(?:\\w+:)?(\\w+)").find(request)?.groupValues?.get(1) ?: ""
  val mediaService = first.contains("media_service")
  val body = if(operation == "GetServiceCapabilities") {
   if(mediaService) "<trt:GetServiceCapabilitiesResponse><trt:Capabilities SnapshotUri=\"false\" Rotation=\"false\" VideoSourceMode=\"false\" OSD=\"false\"><trt:ProfileCapabilities MaximumNumberOfProfiles=\"1\"/><trt:StreamingCapabilities RTPMulticast=\"false\" RTP_TCP=\"true\" RTP_RTSP_TCP=\"true\" NonAggregateControl=\"false\"/></trt:Capabilities></trt:GetServiceCapabilitiesResponse>"
   else "<tds:GetServiceCapabilitiesResponse><tds:Capabilities><tds:Network IPFilter=\"false\" ZeroConfiguration=\"false\" IPVersion6=\"false\" DynDNS=\"false\"/><tds:Security TLS1.0=\"false\" TLS1.1=\"false\" TLS1.2=\"false\" HttpDigest=\"false\" UsernameToken=\"false\"/><tds:System DiscoveryResolve=\"false\" DiscoveryBye=\"false\" RemoteDiscovery=\"false\" SystemBackup=\"false\" SystemLogging=\"false\" FirmwareUpgrade=\"false\"/></tds:Capabilities></tds:GetServiceCapabilitiesResponse>"
  } else Onvif.response(operation, base, host, width,height,fps)
  val messageId = Regex("<(?:\\w+:)?MessageID[^>]*>([a-zA-Z0-9:._-]{1,200})<").find(request)?.groupValues?.get(1)
  val namespace = if(mediaService) "http://www.onvif.org/ver10/media/wsdl" else "http://www.onvif.org/ver10/device/wsdl"
  val addressing = if(messageId != null) "<s:Header><a:Action>$namespace/${operation}Response</a:Action><a:RelatesTo>$messageId</a:RelatesTo><a:MessageID>urn:uuid:${UUID.randomUUID()}</a:MessageID><a:To>http://www.w3.org/2005/08/addressing/anonymous</a:To></s:Header>" else ""
  val payload = """<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">$addressing<s:Body>$body</s:Body></s:Envelope>""".toByteArray()
  val status = if(body.contains("s:Fault")) "500 Internal Server Error" else "200 OK"
  socket.getOutputStream().apply { write("HTTP/1.1 $status\r\nContent-Type: application/soap+xml; charset=utf-8\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
  report("Cam RTC · $host · ONVIF: $operation\nRTSP: rtsp://$host:8554/camera")
 }
 fun setConfig(a: ByteArray,b: ByteArray) { sps = nals(a).firstOrNull() ?: a; pps = nals(b).firstOrNull() ?: b }
 fun frame(bytes: ByteArray, timeUs: Long) {
  val units = nals(bytes); if(units.isEmpty()) return
  for(client in clients) client.offer(units, timeUs)
 }
 private fun rtsp(socket: Socket) {
  val input = socket.getInputStream(); val client = RtspClient(socket); var udp: DatagramSocket? = null
  try { while(active) {
   // Consume client RTCP interleaved packets before the next RTSP request.
   var firstByte = input.read(); if(firstByte < 0) break
   while(firstByte == 36) { input.read(); val n = (input.read() shl 8) or input.read(); require(n in 0..65535); DataInputStream(input).readFully(ByteArray(n)); firstByte = input.read() }
   val request = firstByte.toChar() + line(input); val headers = headers(input); val method = request.substringBefore(' ')
   val length = headers["content-length"]?.toIntOrNull() ?: 0; require(length in 0..65536); if(length > 0) DataInputStream(input).readFully(ByteArray(length))
   var extra = ""; var body = ""; var status = "200 OK"
   when(method) {
    "OPTIONS" -> extra = "Public: OPTIONS, DESCRIBE, SETUP, PLAY, GET_PARAMETER, TEARDOWN\r\n"
    "DESCRIBE" -> {
     if(sps.isEmpty() || pps.isEmpty()) { status = "503 Service Unavailable" } else {
      val profile = sps.drop(1).take(3).joinToString("") { "%02x".format(it.toInt() and 255) }
      body = "v=0\r\no=- 1 1 IN IP4 $host\r\ns=Cam RTC\r\nc=IN IP4 $host\r\nt=0 0\r\na=control:*\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\na=fmtp:96 packetization-mode=1;profile-level-id=$profile;sprop-parameter-sets=${Base64.getEncoder().encodeToString(sps)},${Base64.getEncoder().encodeToString(pps)}\r\na=control:trackID=0\r\n"
      extra = "Content-Type: application/sdp\r\nContent-Base: rtsp://$host:8554/camera/\r\n"
     }
    }
    "SETUP" -> {
     val transport = headers["transport"] ?: ""
     if(transport.contains("RTP/AVP/TCP",true)) {
      client.channel = Regex("interleaved=(\\d+)").find(transport)?.groupValues?.get(1)?.toInt() ?: 0
      extra = "Transport: RTP/AVP/TCP;unicast;interleaved=${client.channel}-${client.channel+1}\r\n"
     } else status = "461 Unsupported Transport"
     if(status == "200 OK") client.setup = true
    }
    "PLAY" -> { if(!client.setup) status = "455 Method Not Valid in This State" }
    "GET_PARAMETER" -> {}
    "TEARDOWN" -> {}
    else -> status = "405 Method Not Allowed"
   }
   client.write("RTSP/1.0 $status\r\nCSeq: ${headers["cseq"] ?: "1"}\r\nSession: ${client.session};timeout=60\r\n$extra${if(body.isNotEmpty()) "Content-Length: ${body.toByteArray().size}\r\n" else ""}\r\n$body".toByteArray())
   if(method == "PLAY" && status == "200 OK") { socket.soTimeout = 90000; clients.addIfAbsent(client); client.waitKey = true; keyFrame() }
   if(method == "TEARDOWN") break
  } } finally { clients.remove(client); client.close(); udp?.close() }
 }
 private inner class RtspClient(val socket: Socket) {
  val session = UUID.randomUUID().toString(); var setup = false; var channel = 0
  var udp: DatagramSocket? = null; var destination: InetSocketAddress? = null
  var seq = Random().nextInt(65536); val ssrc = Random().nextInt()
  private var packets = 0; private var octets = 0; private var lastReport = 0L
  @Volatile var waitKey = true
  private val queue = ArrayBlockingQueue<Pair<List<ByteArray>,Long>>(2)
  private val sender = Thread({ try { while(active && !socket.isClosed) { val next = queue.poll(1,TimeUnit.SECONDS) ?: continue; sendFrame(next.first,next.second) } } catch(_: Exception) { socket.close() } }, "RtpSender").apply { start() }
  @Synchronized fun write(bytes: ByteArray) { socket.getOutputStream().apply { write(bytes); flush() } }
  fun offer(units: List<ByteArray>,time: Long) {
   val key = units.any { it.isNotEmpty() && it[0].toInt() and 31 == 5 }
   if(waitKey && !key) return
   if(key) waitKey = false
   if(!queue.offer(units to time)) { queue.clear(); waitKey = true; keyFrame() }
  }
  private fun sendFrame(units: List<ByteArray>,time: Long) {
   val all = if(units.any { it[0].toInt() and 31 == 5 }) listOf(sps,pps).filter { it.isNotEmpty() } + units else units
   all.forEachIndexed { index, nal ->
    if(nal.size <= 1200) packet(nal,time,index == all.lastIndex) else {
     var offset = 1
     while(offset < nal.size) {
      val length = minOf(1198,nal.size-offset); val end = offset+length == nal.size
      val payload = ByteArray(length+2); payload[0] = ((nal[0].toInt() and 224) or 28).toByte(); payload[1] = ((nal[0].toInt() and 31) or (if(offset == 1) 128 else 0) or (if(end) 64 else 0)).toByte()
      System.arraycopy(nal,offset,payload,2,length); packet(payload,time,end && index == all.lastIndex); offset += length
     }
    }
   }
  }
  private fun packet(payload: ByteArray,time: Long,marker: Boolean) {
   val packet = java.nio.ByteBuffer.allocate(12+payload.size).apply { put(0x80.toByte()); put((96 or if(marker) 128 else 0).toByte()); putShort((seq++).toShort()); putInt((time*90/1000).toInt()); putInt(ssrc); put(payload) }.array()
   packets++; octets += payload.size
   if(System.currentTimeMillis()-lastReport >= 1000) {
    lastReport = System.currentTimeMillis()
    val ntpSeconds = lastReport/1000 + 2208988800L
    val ntpFraction = ((lastReport%1000) shl 32)/1000
    val rtcp = java.nio.ByteBuffer.allocate(28).apply { put(0x80.toByte()); put(200.toByte()); putShort(6); putInt(ssrc); putInt(ntpSeconds.toInt()); putInt(ntpFraction.toInt()); putInt((time*90/1000).toInt()); putInt(packets); putInt(octets) }.array()
    write(byteArrayOf(36,(channel+1).toByte(),0,28) + rtcp)
   }
   val datagram = udp
   if(datagram != null) datagram.send(DatagramPacket(packet,packet.size,destination)) else {
    val framed = byteArrayOf(36,channel.toByte(),(packet.size shr 8).toByte(),packet.size.toByte()) + packet; write(framed)
   }
  }
  fun close() { socket.close(); sender.interrupt() }
 }
 override fun close() {
  active = false; discovery?.close(); listeners.forEach { it.close() }
  sockets.forEach { it.close() }; clients.forEach { it.close() }; pool.shutdownNow()
  pool.awaitTermination(2,TimeUnit.SECONDS)
  sockets.forEach { it.close() }
 }
 companion object {
  fun nals(bytes: ByteArray): List<ByteArray> {
   val starts = mutableListOf<Pair<Int,Int>>(); var i = 0
   while(i+2 < bytes.size) {
    if(bytes[i] == 0.toByte() && bytes[i+1] == 0.toByte()) {
     val length = if(bytes[i+2] == 1.toByte()) 3 else if(i+3 < bytes.size && bytes[i+2] == 0.toByte() && bytes[i+3] == 1.toByte()) 4 else 0
     if(length > 0) { starts.add(i to length); i += length; continue }
    }; i++
   }
   if(starts.isEmpty()) return if(bytes.isEmpty()) emptyList() else listOf(bytes)
   return starts.mapIndexedNotNull { index, start -> val end = if(index+1 < starts.size) starts[index+1].first else bytes.size; if(end > start.first+start.second) bytes.copyOfRange(start.first+start.second,end) else null }
  }
 }
}
