package dev.camrtc

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private lateinit var address: EditText
 private lateinit var quality: Spinner
 private lateinit var start: Button
 private lateinit var status: TextView
 private lateinit var preview: SurfaceViewRenderer
 private lateinit var previewToggle: Switch
 private val worker = Executors.newSingleThreadExecutor()
 private val client = OkHttpClient()
 private lateinit var egl: EglBase
 private lateinit var factory: PeerConnectionFactory
 private var socket: WebSocket? = null
 private var peer: PeerConnection? = null
 private var capturer: CameraVideoCapturer? = null
 private var source: VideoSource? = null
 private var track: VideoTrack? = null
 private var helper: SurfaceTextureHelper? = null
 private var running = false
 @Volatile private var session = 0
 private var negotiation = 0
 private var remoteReady = false
 private val iceQueue = mutableListOf<IceCandidate>()

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  egl = EglBase.create()
  PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions())
  factory = PeerConnectionFactory.builder()
   .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
   .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
  val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
  box.addView(TextView(this).apply { text = "Cam RTC · Video saja"; textSize = 24f })
  address = EditText(this).apply { hint = "IP PC, contoh 192.168.1.10"; setSingleLine(true) }; box.addView(address)
  quality = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("480p / 24 FPS", "720p / 30 FPS", "1080p / 30 FPS")); setSelection(1) }; box.addView(quality)
  start = Button(this).apply { text = "Mulai"; setOnClickListener { if(running) stop() else requestStart() } }; box.addView(start)
  box.addView(Button(this).apply { text = "Ganti kamera"; setOnClickListener { worker.execute { capturer?.switchCamera(null) } } })
  previewToggle = Switch(this).apply { text = "Preview kamera"; isChecked = true }; box.addView(previewToggle)
  preview = SurfaceViewRenderer(this); preview.init(egl.eglBaseContext, null)
  box.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
  previewToggle.setOnCheckedChangeListener { _, enabled ->
   preview.visibility = if(enabled) View.VISIBLE else View.INVISIBLE
   worker.execute { track?.let { if(enabled) it.addSink(preview) else it.removeSink(preview) } }
  }
  status = TextView(this).apply { text = "Hubungkan HP dan PC ke Wi-Fi yang sama." }; box.addView(status)
  setContentView(box)
 }
 private fun show(message: String) { runOnUiThread { status.text = message } }
 private fun requestStart() {
  if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
   requestPermissions(arrayOf(Manifest.permission.CAMERA), 1); return
  }
  val host = address.text.toString().trim()
  if(!Regex("^[a-zA-Z0-9.-]+$").matches(host)) {
   show("Masukkan IP PC."); return
  }
  val preset = quality.selectedItemPosition
  val showPreview = previewToggle.isChecked
  running = true; start.text = "Stop"; address.isEnabled = false; quality.isEnabled = false
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  worker.execute {
   val id = ++session
   try {
    val enumerator = Camera2Enumerator(this)
    val name = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) } ?: enumerator.deviceNames.first()
    capturer = enumerator.createCapturer(name, null) ?: error("Kamera tidak tersedia")
    helper = SurfaceTextureHelper.create("CamRTCCapture", egl.eglBaseContext)
    source = factory.createVideoSource(false)
    capturer!!.initialize(helper, this, source!!.capturerObserver)
    track = factory.createVideoTrack("camera", source!!)
    if(showPreview) track!!.addSink(preview)
    val sizes = arrayOf(intArrayOf(640,480,24),intArrayOf(1280,720,30),intArrayOf(1920,1080,30))
    val size = sizes[preset]; capturer!!.startCapture(size[0],size[1],size[2])
    socket = client.newWebSocket(Request.Builder().url("ws://$host:8787/signal?role=sender").build(), object : WebSocketListener() {
     override fun onOpen(webSocket: WebSocket, response: Response) { show("Menunggu receiver OBS…") }
     override fun onMessage(webSocket: WebSocket, text: String) { worker.execute {
      if(id != session) return@execute
      try {
       val msg = JSONObject(text)
       when(msg.getString("type")) {
        "ready" -> offer()
        "answer" -> {
         val current = peer
         val generation = negotiation
         current?.setRemoteDescription(object : SdpAdapter() {
         override fun onSetSuccess() { worker.execute {
          if(id == session && generation == negotiation && current === peer) { remoteReady = true; iceQueue.forEach { current?.addIceCandidate(it) }; iceQueue.clear() }
         } }
         override fun onSetFailure(error: String?) { show("SDP gagal: $error") }
        }, SessionDescription(SessionDescription.Type.ANSWER,msg.getString("sdp")))
        }
        "ice" -> { val ice = IceCandidate(msg.getString("sdpMid"),msg.getInt("sdpMLineIndex"),msg.getString("candidate"))
         if(remoteReady) peer?.addIceCandidate(ice) else iceQueue.add(ice)
        }
        "peer-left" -> { closePeer(); show("Receiver ditutup. Menunggu OBS…") }
       }
      } catch(e: Exception) { show("Signaling gagal: ${e.message}") }
     } }
     override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { runOnUiThread { if(id == session) { stop(); show("Koneksi gagal: ${t.message}. Periksa IP dan firewall.") } } }
     override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { runOnUiThread { if(id == session) { stop(); show("Koneksi ditutup: $reason") } } }
    })
   } catch(e: Exception) { runOnUiThread { stop(); show("Kamera gagal: ${e.message}") } }
  }
 }
 private fun send(msg: JSONObject) { socket?.send(msg.toString()) }
 private fun offer() {
  closePeer()
  val generation = negotiation
  val id = session
  val config = PeerConnection.RTCConfiguration(emptyList<PeerConnection.IceServer>()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
  peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
   override fun onIceCandidate(c: IceCandidate) { worker.execute {
    if(id == session && generation == negotiation) send(JSONObject().put("type","ice").put("candidate",c.sdp).put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex))
   } }
   override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { show("Video: $state") }
   override fun onSignalingChange(s: PeerConnection.SignalingState) {}
   override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
   override fun onIceConnectionReceivingChange(receiving: Boolean) {}
   override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
   override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
   override fun onAddStream(s: MediaStream) {}
   override fun onRemoveStream(s: MediaStream) {}
   override fun onDataChannel(d: DataChannel) {}
   override fun onRenegotiationNeeded() {}
   override fun onAddTrack(r: RtpReceiver, streams: Array<out MediaStream>) {}
  }) ?: error("PeerConnection gagal")
  peer!!.addTransceiver(track!!, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
  val current = peer!!
  current.createOffer(object : SdpAdapter() {
   override fun onCreateSuccess(sdp: SessionDescription) { worker.execute {
    if(id != session || generation != negotiation || current !== peer) return@execute
    current.setLocalDescription(object : SdpAdapter() {
     override fun onSetSuccess() { worker.execute {
      if(id == session && generation == negotiation && current === peer) send(JSONObject().put("type","offer").put("sdp",sdp.description))
     } }
     override fun onSetFailure(error: String?) { show("SDP lokal gagal: $error") }
    }, sdp)
   } }
   override fun onCreateFailure(error: String?) { show("Offer gagal: $error") }
  }, MediaConstraints())
 }
 private fun stop() {
  running=false; start.text="Mulai"; address.isEnabled=true; quality.isEnabled=true
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  worker.execute { cleanup() }
 }
 private fun closePeer() {
  ++negotiation
  val old = peer; peer = null
  remoteReady=false; iceQueue.clear()
  old?.close(); old?.dispose()
 }
 private fun cleanup() {
  ++session; socket?.close(1000,"Stop"); socket=null
  closePeer()
  try { capturer?.stopCapture() } catch(_: Exception) {}
  capturer?.dispose(); capturer=null
  track?.removeSink(preview); track?.dispose(); track=null
  source?.dispose(); source=null; helper?.dispose(); helper=null
  remoteReady=false; iceQueue.clear()
 }
 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==1 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) requestStart() else show("Izin kamera diperlukan.")
 }
 override fun onPause() { super.onPause(); if(running) stop() }
 override fun onDestroy() {
  worker.execute { cleanup(); factory.dispose(); runOnUiThread { preview.release(); egl.release() } }
  worker.shutdown(); client.dispatcher.executorService.shutdown()
  super.onDestroy()
 }
}
open class SdpAdapter : SdpObserver {
 override fun onCreateSuccess(sdp: SessionDescription) {}
 override fun onSetSuccess() {}
 override fun onCreateFailure(error: String?) {}
 override fun onSetFailure(error: String?) {}
}
