package dev.camrtc

import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory

class ProtocolTest {
 @Test fun annexBHandlesMixedStartCodes() {
  val units = CameraServer.nals(byteArrayOf(0,0,0,1,0x67,5,0,0,1,0x68,6,0,0,1,0x65,7))
  assertEquals(3,units.size); assertArrayEquals(byteArrayOf(0x65,7),units[2])
 }
 @Test fun onvifResponsesAreValidXmlAndExposeOnlyVideo() {
  val operations = listOf("GetDeviceInformation","GetSystemDateAndTime","GetServices","GetCapabilities","GetScopes","GetHostname","GetNetworkInterfaces","GetProfiles","GetProfile","GetVideoSources","GetVideoSourceConfigurations","GetVideoSourceConfiguration","GetVideoEncoderConfigurations","GetVideoEncoderConfiguration","GetVideoEncoderConfigurationOptions","GetStreamUri","Unsupported")
  for(op in operations) {
   val response = Onvif.response(op,"http://192.168.1.2:8080/onvif","192.168.1.2",640,480,24)
   val xml = "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:tds='http://www.onvif.org/ver10/device/wsdl' xmlns:trt='http://www.onvif.org/ver10/media/wsdl' xmlns:tt='http://www.onvif.org/ver10/schema'><s:Body>$response</s:Body></s:Envelope>"
   DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
   assertFalse(response.contains("AudioEncoder"))
  }
 }
 @Test fun rtspStreamsFragmentedH264OverTcpAndRestarts() {
  val server = CameraServer("127.0.0.1",640,480,24,{},{}); server.start()
  try {
   DatagramSocket().use { probe ->
    probe.soTimeout=3000
    val message="<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:a='http://schemas.xmlsoap.org/ws/2004/08/addressing' xmlns:d='http://schemas.xmlsoap.org/ws/2005/04/discovery'><s:Header><a:MessageID>urn:uuid:test</a:MessageID></s:Header><s:Body><d:Probe/></s:Body></s:Envelope>".toByteArray()
    probe.send(DatagramPacket(message,message.size,InetAddress.getByName("127.0.0.1"),3702))
    val reply=DatagramPacket(ByteArray(8192),8192); probe.receive(reply)
    val xml=String(reply.data,0,reply.length)
    assertTrue(xml.contains("ProbeMatches")); assertTrue(xml.contains("<a:RelatesTo>urn:uuid:test</a:RelatesTo>")); assertTrue(xml.contains("http://127.0.0.1:8080/onvif/device_service"))
   }
   val connection=URL("http://127.0.0.1:8080/onvif/media_service").openConnection() as HttpURLConnection
   connection.connectTimeout=3000; connection.readTimeout=3000; connection.requestMethod="POST"; connection.doOutput=true
   connection.outputStream.use { it.write("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' xmlns:trt='http://www.onvif.org/ver10/media/wsdl'><s:Body><trt:GetStreamUri/></s:Body></s:Envelope>".toByteArray()) }
   assertEquals(200,connection.responseCode)
   assertTrue(connection.inputStream.bufferedReader().use { it.readText() }.contains("rtsp://127.0.0.1:8554/camera")); connection.disconnect()
   server.setConfig(byteArrayOf(0,0,0,1,0x67,0x42,0,0x1f),byteArrayOf(0,0,0,1,0x68,1))
   Socket("127.0.0.1",8554).use { socket ->
    socket.soTimeout=3000
    val input=DataInputStream(socket.getInputStream()); val output=socket.getOutputStream()
    fun request(method: String, extra: String=""): String {
     output.write("$method rtsp://127.0.0.1:8554/camera RTSP/1.0\r\nCSeq: 1\r\n$extra\r\n".toByteArray()); output.flush()
     val headers=StringBuilder()
     while(true) { val line=input.readLine() ?: error("Disconnected"); headers.append(line).append('\n'); if(line.isEmpty()) break }
     val length=Regex("Content-Length: (\\d+)").find(headers)?.groupValues?.get(1)?.toInt() ?: 0
     if(length>0) { val body=ByteArray(length); input.readFully(body); headers.append(String(body)) }
     return headers.toString()
    }
    assertTrue(request("DESCRIBE").contains("H264/90000"))
    assertTrue(request("SETUP","Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n").contains("200 OK"))
    assertTrue(request("PLAY").contains("200 OK"))
    val nal=ByteArray(2500) { 3 }; nal[0]=0x65
    server.frame(byteArrayOf(0,0,0,1)+nal,1000000)
    val fragments=mutableListOf<ByteArray>(); var markers=0
    while(fragments.size<3) {
     assertEquals(36,input.readUnsignedByte()); val channel=input.readUnsignedByte(); val n=input.readUnsignedShort(); val packet=ByteArray(n); input.readFully(packet)
     if(channel==0 && packet[12].toInt() and 31 == 28) { fragments.add(packet.copyOfRange(14,packet.size)); if(packet[1].toInt() and 128 != 0) markers++ }
    }
    assertArrayEquals(nal.copyOfRange(1,nal.size),fragments.reduce { a,b -> a+b }); assertEquals(1,markers)
    assertTrue(request("TEARDOWN").contains("200 OK"))
   }
  } finally { server.close() }
  CameraServer("127.0.0.1",640,480,24,{},{}).use { it.start() }
 }
}
