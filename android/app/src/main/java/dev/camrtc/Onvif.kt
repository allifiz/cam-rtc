package dev.camrtc

import java.time.ZonedDateTime
import java.time.ZoneOffset

/** Small, read-only ONVIF device/media endpoint. Unsupported operations return a SOAP fault. */
object Onvif {
 fun response(op: String, base: String, host: String, width: Int, height: Int, fps: Int): String {
  val source = "<tt:Name>Camera</tt:Name><tt:UseCount>1</tt:UseCount><tt:SourceToken>camera</tt:SourceToken><tt:Bounds x=\"0\" y=\"0\" width=\"$width\" height=\"$height\"/>"
  val encoder = "<tt:Name>H264</tt:Name><tt:UseCount>1</tt:UseCount><tt:Encoding>H264</tt:Encoding><tt:Resolution><tt:Width>$width</tt:Width><tt:Height>$height</tt:Height></tt:Resolution><tt:Quality>5</tt:Quality><tt:RateControl><tt:FrameRateLimit>$fps</tt:FrameRateLimit><tt:EncodingInterval>1</tt:EncodingInterval><tt:BitrateLimit>${if(width>1000) 2500 else 900}</tt:BitrateLimit></tt:RateControl><tt:H264><tt:GovLength>$fps</tt:GovLength><tt:H264Profile>Baseline</tt:H264Profile></tt:H264><tt:Multicast><tt:Address><tt:Type>IPv4</tt:Type><tt:IPv4Address>0.0.0.0</tt:IPv4Address></tt:Address><tt:Port>0</tt:Port><tt:TTL>1</tt:TTL><tt:AutoStart>false</tt:AutoStart></tt:Multicast><tt:SessionTimeout>PT60S</tt:SessionTimeout>"
  val profile = "<tt:Name>Cam RTC</tt:Name><tt:VideoSourceConfiguration token=\"source\">$source</tt:VideoSourceConfiguration><tt:VideoEncoderConfiguration token=\"encoder\">$encoder</tt:VideoEncoderConfiguration>"
  fun device(body: String) = "<tds:${op}Response>$body</tds:${op}Response>"
  fun media(body: String) = "<trt:${op}Response>$body</trt:${op}Response>"
  return when(op) {
   "GetDeviceInformation" -> device("<tds:Manufacturer>Cam RTC</tds:Manufacturer><tds:Model>Android Network Camera</tds:Model><tds:FirmwareVersion>0.2.0</tds:FirmwareVersion><tds:SerialNumber>camrtc-$host</tds:SerialNumber><tds:HardwareId>Android</tds:HardwareId>")
   "GetSystemDateAndTime" -> { val now = ZonedDateTime.now(ZoneOffset.UTC); device("<tds:SystemDateAndTime><tt:DateTimeType>NTP</tt:DateTimeType><tt:DaylightSavings>false</tt:DaylightSavings><tt:UTCDateTime><tt:Time><tt:Hour>${now.hour}</tt:Hour><tt:Minute>${now.minute}</tt:Minute><tt:Second>${now.second}</tt:Second></tt:Time><tt:Date><tt:Year>${now.year}</tt:Year><tt:Month>${now.monthValue}</tt:Month><tt:Day>${now.dayOfMonth}</tt:Day></tt:Date></tt:UTCDateTime></tds:SystemDateAndTime>") }
   "GetServices" -> device(listOf("device" to "tds", "media" to "trt").joinToString("") { (name, _) -> "<tds:Service><tds:Namespace>http://www.onvif.org/ver10/$name/wsdl</tds:Namespace><tds:XAddr>$base/${name}_service</tds:XAddr><tds:Version><tt:Major>2</tt:Major><tt:Minor>0</tt:Minor></tds:Version></tds:Service>" })
   "GetCapabilities" -> device("<tds:Capabilities><tt:Device><tt:XAddr>$base/device_service</tt:XAddr><tt:Network><tt:IPFilter>false</tt:IPFilter><tt:ZeroConfiguration>false</tt:ZeroConfiguration><tt:IPVersion6>false</tt:IPVersion6><tt:DynDNS>false</tt:DynDNS></tt:Network><tt:System><tt:DiscoveryResolve>false</tt:DiscoveryResolve><tt:DiscoveryBye>false</tt:DiscoveryBye><tt:RemoteDiscovery>false</tt:RemoteDiscovery><tt:SystemBackup>false</tt:SystemBackup><tt:SystemLogging>false</tt:SystemLogging><tt:FirmwareUpgrade>false</tt:FirmwareUpgrade><tt:SupportedVersions><tt:Major>2</tt:Major><tt:Minor>0</tt:Minor></tt:SupportedVersions></tt:System><tt:IO/><tt:Security><tt:TLS1.1>false</tt:TLS1.1><tt:TLS1.2>false</tt:TLS1.2><tt:OnboardKeyGeneration>false</tt:OnboardKeyGeneration><tt:AccessPolicyConfig>false</tt:AccessPolicyConfig><tt:X.509Token>false</tt:X.509Token><tt:SAMLToken>false</tt:SAMLToken><tt:KerberosToken>false</tt:KerberosToken><tt:RELToken>false</tt:RELToken></tt:Security></tt:Device><tt:Media><tt:XAddr>$base/media_service</tt:XAddr><tt:StreamingCapabilities><tt:RTPMulticast>false</tt:RTPMulticast><tt:RTP_TCP>true</tt:RTP_TCP><tt:RTP_RTSP_TCP>true</tt:RTP_RTSP_TCP></tt:StreamingCapabilities></tt:Media></tds:Capabilities>")
   "GetScopes" -> device(listOf("name/Cam%20RTC","hardware/Android","type/video_encoder","Profile/Streaming").joinToString("") { "<tds:Scopes><tt:ScopeDef>Fixed</tt:ScopeDef><tt:ScopeItem>onvif://www.onvif.org/$it</tt:ScopeItem></tds:Scopes>" })
   "GetHostname" -> device("<tds:HostnameInformation><tt:FromDHCP>false</tt:FromDHCP><tt:Name>Cam RTC</tt:Name></tds:HostnameInformation>")
   "GetNetworkInterfaces" -> device("<tds:NetworkInterfaces token=\"wifi\"><tt:Enabled>true</tt:Enabled><tt:Info><tt:Name>wifi</tt:Name><tt:HwAddress>02:00:00:00:00:00</tt:HwAddress><tt:MTU>1500</tt:MTU></tt:Info><tt:IPv4><tt:Enabled>true</tt:Enabled><tt:Config><tt:Manual><tt:Address>$host</tt:Address><tt:PrefixLength>24</tt:PrefixLength></tt:Manual><tt:DHCP>false</tt:DHCP></tt:Config></tt:IPv4></tds:NetworkInterfaces>")
   "GetProfiles" -> media("<trt:Profiles token=\"main\" fixed=\"true\">$profile</trt:Profiles>")
   "GetProfile" -> media("<trt:Profile token=\"main\" fixed=\"true\">$profile</trt:Profile>")
   "GetVideoSources" -> media("<trt:VideoSources token=\"camera\"><tt:Framerate>$fps</tt:Framerate><tt:Resolution><tt:Width>$width</tt:Width><tt:Height>$height</tt:Height></tt:Resolution></trt:VideoSources>")
   "GetVideoSourceConfigurations" -> media("<trt:Configurations token=\"source\">$source</trt:Configurations>")
   "GetVideoSourceConfiguration" -> media("<trt:Configuration token=\"source\">$source</trt:Configuration>")
   "GetVideoEncoderConfigurations" -> media("<trt:Configurations token=\"encoder\">$encoder</trt:Configurations>")
   "GetVideoEncoderConfiguration" -> media("<trt:Configuration token=\"encoder\">$encoder</trt:Configuration>")
   "GetVideoEncoderConfigurationOptions" -> media("<trt:Options><tt:QualityRange><tt:Min>1</tt:Min><tt:Max>10</tt:Max></tt:QualityRange><tt:H264><tt:ResolutionsAvailable><tt:Width>$width</tt:Width><tt:Height>$height</tt:Height></tt:ResolutionsAvailable><tt:GovLengthRange><tt:Min>$fps</tt:Min><tt:Max>$fps</tt:Max></tt:GovLengthRange><tt:FrameRateRange><tt:Min>$fps</tt:Min><tt:Max>$fps</tt:Max></tt:FrameRateRange><tt:EncodingIntervalRange><tt:Min>1</tt:Min><tt:Max>1</tt:Max></tt:EncodingIntervalRange><tt:H264ProfilesSupported>Baseline</tt:H264ProfilesSupported></tt:H264></trt:Options>")
   "GetStreamUri" -> media("<trt:MediaUri><tt:Uri>rtsp://$host:8554/camera</tt:Uri><tt:InvalidAfterConnect>false</tt:InvalidAfterConnect><tt:InvalidAfterReboot>false</tt:InvalidAfterReboot><tt:Timeout>PT0S</tt:Timeout></trt:MediaUri>")
   else -> "<s:Fault><s:Code><s:Value>s:Sender</s:Value></s:Code><s:Reason><s:Text xml:lang=\"en\">Operation not supported</s:Text></s:Reason></s:Fault>"
  }
 }
}
