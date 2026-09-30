package dev.camrtc

import android.content.Context
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import java.net.*
import java.util.Collections


/** Independent surface encoder: no pixel copies and no simultaneous WebRTC encoder. */
class NetworkCamera(private val context: Context, private val report: (String) -> Unit) {
 private val thread = HandlerThread("NetworkCamera")
 private var camera: CameraDevice? = null
 private var capture: CameraCaptureSession? = null
 private var codec: MediaCodec? = null
 private var surface: android.view.Surface? = null
 private var server: CameraServer? = null
 private var lock: WifiManager.MulticastLock? = null
 @Volatile private var active = false
 private var drain: Thread? = null
 fun start(width: Int, height: Int, fps: Int) {
  active = true
  try {
   val host = Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress ?: error("Wi-Fi belum terhubung")
   lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("camrtc-discovery").apply { setReferenceCounted(false); acquire() }
   val encoder = MediaCodec.createEncoderByType("video/avc"); codec = encoder
   val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    setInteger(MediaFormat.KEY_BIT_RATE, if(width > 1000) 2_500_000 else 900_000)
    setInteger(MediaFormat.KEY_FRAME_RATE, fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
   }
   encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
   surface = encoder.createInputSurface(); encoder.start()
   server = CameraServer(host, width, height, fps, report) { try { encoder.setParameters(android.os.Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) } catch (_: Exception) {} }.also { it.start() }
   thread.start()
   val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
   val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: manager.cameraIdList.first()
   manager.openCamera(id, object : CameraDevice.StateCallback() {
    override fun onOpened(device: CameraDevice) {
     if(!active) { device.close(); return }; camera = device
     device.createCaptureSession(listOf(surface!!), object : CameraCaptureSession.StateCallback() {
      override fun onConfigured(session: CameraCaptureSession) {
       if(!active) { session.close(); return }; capture = session
       try {
       val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(surface!!); set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        val ranges = manager.getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val range = ranges?.filter { it.contains(fps) }?.minByOrNull { it.upper-it.lower }
        if(range != null) set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range)
       }
       session.setRepeatingRequest(request.build(), null, Handler(thread.looper))
       report("Cam RTC ONVIF aktif · $host\nCari kamera di Windows. RTSP: rtsp://$host:8554/camera")
       } catch(e: Exception) { report("Capture gagal: ${e.message}. Tekan Stop.") }
      }
      override fun onConfigureFailed(session: CameraCaptureSession) { report("Konfigurasi kamera gagal. Tekan Stop.") }
     }, Handler(thread.looper))
    }
    override fun onDisconnected(device: CameraDevice) { device.close(); report("Kamera terputus. Tekan Stop.") }
    override fun onError(device: CameraDevice, error: Int) { device.close(); report("Kamera gagal ($error). Tekan Stop.") }
   }, Handler(thread.looper))
   drain = Thread({
    val info = MediaCodec.BufferInfo()
    try { while(active) {
     val index = encoder.dequeueOutputBuffer(info, 10000)
     if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      val output = encoder.outputFormat
      val sps = output.getByteBuffer("csd-0"); val pps = output.getByteBuffer("csd-1")
      if(sps != null && pps != null) { val a = ByteArray(sps.remaining()); sps.get(a); val b = ByteArray(pps.remaining()); pps.get(b); server?.setConfig(a,b) }
     } else if(index >= 0) {
      val buffer = encoder.getOutputBuffer(index)
      if(buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
       buffer.position(info.offset); buffer.limit(info.offset + info.size)
       val bytes = ByteArray(info.size); buffer.get(bytes); server?.frame(bytes, info.presentationTimeUs)
      }
      encoder.releaseOutputBuffer(index, false)
     }
    } } catch(e: Exception) { if(active) report("Encoder gagal: ${e.message}. Tekan Stop.") }
   }, "H264Drain").also { it.start() }
  } catch(e: Exception) { stop(); throw e }
 }
 fun stop() {
  active = false
  capture?.close(); capture = null; camera?.close(); camera = null
  server?.close(); server = null
  drain?.join(1500); drain = null
  try { codec?.stop() } catch(_: Exception) {}; codec?.release(); codec = null
  surface?.release(); surface = null
  thread.quitSafely(); if(thread.isAlive) thread.join(1500)
  lock?.let { if(it.isHeld) it.release() }; lock = null
 }
}
