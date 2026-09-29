package com.recharge.client.features.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.recharge.client.BuildConfig
import com.recharge.client.core.model.CallIceServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.DataChannel
import org.webrtc.MediaStream
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import java.util.concurrent.atomic.AtomicBoolean

enum class VoiceCallPhase {
    CONNECTING,
    CONNECTED,
    ENDED,
    ERROR
}

data class AudioOutputOption(
    val id: String,
    val label: String
)

data class VoiceCallEngineState(
    val phase: VoiceCallPhase = VoiceCallPhase.CONNECTING,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val audioOutputId: String = "EARPIECE",
    val audioOutput: String = "Phone",
    val audioOutputs: List<AudioOutputOption> = emptyList(),
    val message: String = "Connecting securely…",
    val connectedAtEpochMillis: Long? = null,
    val endedAtEpochMillis: Long? = null
)

class VoiceCallEngine(private val context: Context) {
    private val gson = Gson()
    private val stateFlow = MutableStateFlow(VoiceCallEngineState())
    val state: StateFlow<VoiceCallEngineState> = stateFlow

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val signalingHandler = Handler(Looper.getMainLooper())
    private var signalingRetryRunnable: Runnable? = null
    private var signalingConnectWatchdog: Runnable? = null
    private var signalingRetryAttempt = 0
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private val initialized = AtomicBoolean(false)
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (stateFlow.value.phase != VoiceCallPhase.ENDED) {
                    stateFlow.value = stateFlow.value.copy(message = "Audio is temporarily unavailable…")
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (stateFlow.value.phase == VoiceCallPhase.CONNECTED) {
                    stateFlow.value = stateFlow.value.copy(message = "Connected securely")
                }
            }
        }
    }
    private var callId: String = ""

    companion object {
        private const val TAG = "VoiceCallEngine"
        private val factoryInitialized = AtomicBoolean(false)
    }

    fun start(callId: String, signalingToken: String, websocketPath: String, iceServers: List<CallIceServer>) {
        stop()
        this.callId = callId
        stateFlow.value = VoiceCallEngineState()

        runCatching {
            setupAudio()
            setupPeerConnectionFactory()
            setupPeerConnection(iceServers)
            connectWebSocket(signalingToken, websocketPath)
            initialized.set(true)
        }.onFailure {
            stateFlow.value = VoiceCallEngineState(phase = VoiceCallPhase.ERROR, message = it.message ?: "Unable to start voice call")
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        stateFlow.value = stateFlow.value.copy(muted = muted)
    }

    fun toggleSpeaker() {
        setAudioOutput(if (stateFlow.value.speaker) "EARPIECE" else "SPEAKER")
    }

    fun refreshAudioOutputs() {
        val manager = audioManager ?: return
        stateFlow.value = stateFlow.value.copy(audioOutputs = buildAudioOutputs(manager))
    }

    fun setAudioOutput(outputId: String) {
        val manager = audioManager ?: return
        val output = buildAudioOutputs(manager).firstOrNull { it.id == outputId } ?: return
        val applied = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val deviceTypes = when (outputId) {
                    "EARPIECE" -> setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                    "SPEAKER" -> setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    "BLUETOOTH" -> setOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_HEARING_AID
                    )
                    else -> setOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET
                    )
                }
                val device = manager.availableCommunicationDevices.firstOrNull { it.type in deviceTypes }
                device != null && manager.setCommunicationDevice(device)
            } else {
                @Suppress("DEPRECATION")
                when (outputId) {
                    "SPEAKER" -> {
                        manager.stopBluetoothSco()
                        manager.isBluetoothScoOn = false
                        manager.isSpeakerphoneOn = true
                        true
                    }
                    "BLUETOOTH" -> {
                        manager.startBluetoothSco()
                        manager.isBluetoothScoOn = true
                        true
                    }
                    else -> {
                        manager.stopBluetoothSco()
                        manager.isBluetoothScoOn = false
                        manager.isSpeakerphoneOn = false
                        true
                    }
                }
            }
        }.getOrDefault(false)

        if (applied) {
            stateFlow.value = stateFlow.value.copy(
                speaker = outputId == "SPEAKER",
                audioOutputId = outputId,
                audioOutput = output.label
            )
        } else if (outputId == "BLUETOOTH") {
            stateFlow.value = stateFlow.value.copy(message = "Connect a Bluetooth audio device and try again")
        }
    }

    private fun buildAudioOutputs(manager: AudioManager): List<AudioOutputOption> {
        val outputs = mutableListOf(
            AudioOutputOption("EARPIECE", "Phone"),
            AudioOutputOption("SPEAKER", "Speaker")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                outputs.add(AudioOutputOption("BLUETOOTH", "Bluetooth"))
                return outputs
            }
            val devices = runCatching { manager.availableCommunicationDevices }.getOrDefault(emptyList())
            devices.filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }.mapNotNull { device ->
                val name = device.productName?.toString()?.trim().orEmpty()
                AudioOutputOption("BLUETOOTH", if (name.isBlank()) "Bluetooth" else "Bluetooth · $name")
            }.distinctBy { it.label }.firstOrNull()?.let { outputs.add(it) }

            if (devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }) {
                outputs.add(AudioOutputOption("WIRED", "Wired headset"))
            }
        } else {
            @Suppress("DEPRECATION")
            if (manager.isBluetoothScoAvailableOffCall) {
                outputs.add(AudioOutputOption("BLUETOOTH", "Bluetooth"))
            }
        }
        return outputs
    }

    fun stop() {
        if (!initialized.getAndSet(false) && peerConnection == null && webSocket == null) return
        signalingRetryRunnable?.let(signalingHandler::removeCallbacks)
        signalingRetryRunnable = null
        signalingConnectWatchdog?.let(signalingHandler::removeCallbacks)
        signalingConnectWatchdog = null
        signalingRetryAttempt = 0
        runCatching { webSocket?.close(1000, "call ended") }
        webSocket = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null
        pendingRemoteCandidates.clear()
        remoteDescriptionSet = false
        restoreAudio()
    }

    private fun setupAudio() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        previousAudioMode = manager.mode
        @Suppress("DEPRECATION")
        previousSpeakerState = manager.isSpeakerphoneOn

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = null
            throw IllegalStateException("Audio focus is currently unavailable")
        }

        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val outputs = buildAudioOutputs(manager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece = runCatching {
                manager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
            }.getOrNull()
            if (earpiece != null) {
                runCatching { manager.setCommunicationDevice(earpiece) }
            } else {
                runCatching { manager.clearCommunicationDevice() }
            }
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = false
        }
        stateFlow.value = stateFlow.value.copy(
            speaker = false,
            audioOutputId = "EARPIECE",
            audioOutput = "Phone",
            audioOutputs = outputs
        )
    }

    private fun restoreAudio() {
        audioManager?.let { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = previousSpeakerState
            }
            manager.mode = previousAudioMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusListener)
            }
        }
        audioFocusRequest = null
        audioManager = null
    }

    private fun setupPeerConnectionFactory() {
        if (factoryInitialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context.applicationContext)
                    .createInitializationOptions()
            )
        }
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val constraints = MediaConstraints()
        audioSource = peerConnectionFactory!!.createAudioSource(constraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("mpay-microphone", audioSource)
        localAudioTrack!!.setEnabled(true)
    }

    private fun setupPeerConnection(iceServers: List<CallIceServer>) {
        val servers = iceServers.flatMap { server ->
            server.urls.map { url ->
                val builder = PeerConnection.IceServer.builder(url)
                if (!server.username.isNullOrBlank()) builder.setUsername(server.username)
                if (!server.credential.isNullOrBlank()) builder.setPassword(server.credential)
                builder.createIceServer()
            }
        }

        val configuration = PeerConnection.RTCConfiguration(servers)
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory?.createPeerConnection(
            configuration,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.i(TAG, "ICE state=$newState callId=$callId")
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            stateFlow.value = stateFlow.value.copy(
                                phase = VoiceCallPhase.CONNECTED,
                                message = "Connected securely",
                                connectedAtEpochMillis = stateFlow.value.connectedAtEpochMillis
                                    ?: System.currentTimeMillis()
                            )
                            sendType("connected")
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            stateFlow.value = VoiceCallEngineState(
                                phase = VoiceCallPhase.ERROR,
                                muted = stateFlow.value.muted,
                                speaker = stateFlow.value.speaker,
                                message = "The secure audio connection could not be established"
                            )
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (stateFlow.value.phase != VoiceCallPhase.ENDED) {
                                stateFlow.value = stateFlow.value.copy(message = "Reconnecting audio…")
                            }
                        }
                        else -> Unit
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

                override fun onIceCandidate(candidate: IceCandidate) {
                    val payload = JsonObject().apply {
                        addProperty("kind", "candidate")
                        candidate.sdpMid?.let { addProperty("sdpMid", it) }
                        addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                        addProperty("candidate", candidate.sdp)
                    }
                    sendSignal(payload)
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
                override fun onAddStream(stream: MediaStream) = Unit
                override fun onRemoveStream(stream: MediaStream) = Unit
                override fun onDataChannel(dataChannel: DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
                    Log.i(TAG, "Remote RTP track received. callId=" + callId)
                }
                override fun onTrack(transceiver: RtpTransceiver) {
                    Log.i(TAG, "Remote RTP transceiver received. callId=" + callId)
                }
            }
        )

        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("mpay-audio"))
        }
    }

    private fun connectWebSocket(token: String, websocketPath: String) {
        val base = BuildConfig.MPAY_API_BASE_URL.trimEnd('/')
        val socketBase = when {
            base.startsWith("https://", true) -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://", true) -> "ws://" + base.removePrefix("http://")
            else -> base
        }
        val url = socketBase + websocketPath + "?token=" + Uri.encode(token)
        stateFlow.value = stateFlow.value.copy(message = "Connecting to secure signaling…")
        val opening = AtomicBoolean(true)

        webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(socket: WebSocket, response: Response) {
                    opening.set(false)
                    signalingConnectWatchdog?.let(signalingHandler::removeCallbacks)
                    signalingConnectWatchdog = null
                    signalingRetryAttempt = 0
                    signalingRetryRunnable?.let(signalingHandler::removeCallbacks)
                    signalingRetryRunnable = null
                    Log.i(TAG, "Signaling WebSocket connected. callId=$callId")
                    sendType("ready")
                    stateFlow.value = stateFlow.value.copy(message = "Waiting for secure audio…")
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    Log.i(
                        TAG,
                        "Signaling message received. callId=" + callId +
                            " type=" + (runCatching {
                                gson.fromJson(text, JsonObject::class.java)?.get("type")?.asString
                            }.getOrNull() ?: "unknown")
                    )
                    handleMessage(text)
                }

                override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                    opening.set(false)
                    signalingConnectWatchdog?.let(signalingHandler::removeCallbacks)
                    signalingConnectWatchdog = null
                    Log.e(
                        TAG,
                        "Signaling WebSocket failed callId=" + callId +
                            " httpCode=" + (response?.code ?: -1) +
                            " iceState=" + (peerConnection?.iceConnectionState() ?: "none") +
                            " message=" + t.message,
                        t
                    )
                    if (stateFlow.value.phase != VoiceCallPhase.CONNECTED &&
                        stateFlow.value.phase != VoiceCallPhase.ENDED
                    ) {
                        scheduleSignalingReconnect(token, websocketPath)
                    }
                }

                override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                    socket.close(code, reason)
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    opening.set(false)
                    signalingConnectWatchdog?.let(signalingHandler::removeCallbacks)
                    signalingConnectWatchdog = null
                    if (stateFlow.value.phase != VoiceCallPhase.CONNECTED &&
                        stateFlow.value.phase != VoiceCallPhase.ENDED
                    ) {
                        scheduleSignalingReconnect(token, websocketPath)
                    }
                }
            }
        )

        val watchdog = Runnable {
            if (opening.compareAndSet(true, false)) {
                Log.w(TAG, "Signaling WebSocket open timeout. callId=" + callId)
                stateFlow.value = stateFlow.value.copy(message = "Reconnecting secure signaling…")
                webSocket?.cancel()
            }
        }
        signalingConnectWatchdog = watchdog
        signalingHandler.postDelayed(watchdog, 12_000L)
    }

    private fun scheduleSignalingReconnect(token: String, websocketPath: String) {
        if (signalingRetryRunnable != null ||
            stateFlow.value.phase == VoiceCallPhase.CONNECTED ||
            stateFlow.value.phase == VoiceCallPhase.ENDED
        ) return

        signalingRetryAttempt += 1
        val delayMs = (1000L * signalingRetryAttempt.coerceAtMost(5))
        stateFlow.value = stateFlow.value.copy(message = "Reconnecting secure signaling…")
        val retry = Runnable {
            signalingRetryRunnable = null
            if (stateFlow.value.phase != VoiceCallPhase.CONNECTED &&
                stateFlow.value.phase != VoiceCallPhase.ENDED
            ) {
                connectWebSocket(token, websocketPath)
            }
        }
        signalingRetryRunnable = retry
        signalingHandler.postDelayed(retry, delayMs)
    }

    private fun handleMessage(text: String) {
        val root = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
        if (root.get("callId")?.asString != callId) return
        when (root.get("type")?.asString) {
            "ready" -> {
                Log.i(TAG, "Both call participants are ready. callId=" + callId)
                stateFlow.value = stateFlow.value.copy(message = "Preparing secure audio…")
            }
            "status" -> {
                when (root.get("status")?.asString) {
                    "CONNECTED" -> {
                        val serverConnectedAt = root.get("connectedAtEpochMillis")
                            ?.takeIf { !it.isJsonNull }
                            ?.asLong
                        stateFlow.value = stateFlow.value.copy(
                            phase = VoiceCallPhase.CONNECTED,
                            message = "Connected securely",
                            connectedAtEpochMillis = serverConnectedAt
                                ?: stateFlow.value.connectedAtEpochMillis
                                ?: System.currentTimeMillis()
                        )
                    }
                    "DECLINED", "MISSED", "CANCELLED", "ENDED" -> {
                        stateFlow.value = stateFlow.value.copy(
                            phase = VoiceCallPhase.ENDED,
                            message = when (root.get("status")?.asString) {
                                "DECLINED" -> "Call declined"
                                "MISSED" -> "Call missed"
                                "CANCELLED" -> "Call cancelled"
                                else -> "Call ended"
                            },
                            endedAtEpochMillis = root.get("endedAtEpochMillis")
                                ?.takeIf { !it.isJsonNull }
                                ?.asLong
                                ?: System.currentTimeMillis()
                        )
                    }
                }
            }
            "signal" -> root.getAsJsonObject("payload")?.let(::handleSignal)
        }
    }

    private fun handleSignal(payload: JsonObject) {
        when (payload.get("kind")?.asString) {
            "offer" -> {
                val type = payload.get("type")?.asString ?: "offer"
                val sdp = payload.get("sdp")?.asString ?: return
                val description = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        flushCandidates()
                        createAnswer()
                    }
                }, description)
            }
            "candidate" -> {
                val candidate = IceCandidate(
                    payload.get("sdpMid")?.asString,
                    payload.get("sdpMLineIndex")?.asInt ?: 0,
                    payload.get("candidate")?.asString ?: return
                )
                if (remoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
                else pendingRemoteCandidates += candidate
            }
        }
    }

    private fun flushCandidates() {
        pendingRemoteCandidates.toList().forEach { peerConnection?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sendDescription("answer", description)
                    }
                }, description)
            }

            override fun onCreateFailure(error: String) {
                stateFlow.value = stateFlow.value.copy(phase = VoiceCallPhase.ERROR, message = "Unable to prepare secure audio")
            }
        }, MediaConstraints())
    }

    private fun sendDescription(kind: String, description: SessionDescription) {
        val payload = JsonObject().apply {
            addProperty("kind", kind)
            addProperty("type", description.type.canonicalForm())
            addProperty("sdp", description.description)
        }
        sendSignal(payload)
    }

    private fun sendSignal(payload: JsonObject) {
        val root = JsonObject().apply {
            addProperty("type", "signal")
            addProperty("callId", callId)
            add("payload", payload)
        }
        webSocket?.send(gson.toJson(root))
    }

    private fun sendType(type: String) {
        val root = JsonObject().apply {
            addProperty("type", type)
            addProperty("callId", callId)
        }
        webSocket?.send(gson.toJson(root))
    }

    abstract class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}
