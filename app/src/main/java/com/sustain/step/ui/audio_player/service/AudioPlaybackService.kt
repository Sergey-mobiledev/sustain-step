package com.sustain.step.ui.audio_player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
import android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.MediaSessionCompat
import com.sustain.step.MainActivity
import com.sustain.step.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentUri: String? = null
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var queueUris: List<String> = emptyList()
    private var queueTitles: List<String> = emptyList()
    private var queueArtists: List<String> = emptyList()
    private var currentIndex: Int = -1
    private var foregroundStarted = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                mediaPlayer?.setVolume(1f, 1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    startPlaybackAfterFocusGranted()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause(abandonAudioFocus = true)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = mediaPlayer?.isPlaying == true
                pause(abandonAudioFocus = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "SustainStepPlayer").apply {
            setFlags(FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = toggle()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) {
                    mediaPlayer?.seekTo(pos.toInt().coerceAtLeast(0))
                    publishState()
                }
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
        updateNotification(isPlaying = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                if (index >= 0 && queueUris.isNotEmpty()) {
                    playFromQueue(index)
                } else {
                    val uri = intent.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
                    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                    val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                    play(uri, title, artist)
                }
            }

            ACTION_TOGGLE -> toggle()
            ACTION_PAUSE -> pause()
            ACTION_PREVIOUS -> previous()
            ACTION_NEXT -> next()
            ACTION_SET_QUEUE -> setQueue(
                uris = intent.getStringArrayListExtra(EXTRA_QUEUE_URIS).orEmpty(),
                titles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES).orEmpty(),
                artists = intent.getStringArrayListExtra(EXTRA_QUEUE_ARTISTS).orEmpty(),
                index = intent.getIntExtra(EXTRA_INDEX, -1)
            )
            ACTION_SEEK -> {
                val pos = intent.getIntExtra(EXTRA_POSITION_MS, 0)
                mediaPlayer?.seekTo(pos.coerceAtLeast(0))
                publishState()
            }

            ACTION_STOP -> stopSelf()
            Intent.ACTION_MEDIA_BUTTON -> MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        progressJob?.cancel()
        progressJob = null
        abandonAudioFocus()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        PlaybackStateStore.update(PlaybackState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        pause()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun play(uri: String, title: String, artist: String) {
        if (!requestAudioFocus()) return
        currentUri = uri
        currentTitle = title
        currentArtist = artist

        val needsNewPlayer = mediaPlayer == null || PlaybackStateStore.state.value.currentUri?.toString() != uri
        if (needsNewPlayer) {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, android.net.Uri.parse(uri))
            mediaPlayer?.setOnCompletionListener {
                if (queueUris.size > 1 && currentIndex in queueUris.indices) {
                    next()
                } else {
                    pause()
                    mediaPlayer?.seekTo(0)
                    publishState()
                }
            }
        }

        val player = mediaPlayer ?: return
        startPlaybackAfterFocusGranted(player)
    }

    private fun playFromQueue(index: Int) {
        if (queueUris.isEmpty()) return
        val safeIndex = index.coerceIn(0, queueUris.lastIndex)
        currentIndex = safeIndex
        play(
            uri = queueUris[safeIndex],
            title = queueTitles.getOrElse(safeIndex) { "" },
            artist = queueArtists.getOrElse(safeIndex) { "" }
        )
    }

    private fun toggle() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            if (!requestAudioFocus()) return
            startPlaybackAfterFocusGranted(player)
        }
    }

    private fun pause(abandonAudioFocus: Boolean = true) {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        if (abandonAudioFocus) {
            resumeOnFocusGain = false
            abandonAudioFocus()
        }
        progressJob?.cancel()
        progressJob = null
        publishState()
        updateNotification(isPlaying = false)
    }

    private fun startPlaybackAfterFocusGranted(player: MediaPlayer? = mediaPlayer) {
        val targetPlayer = player ?: return
        if (!targetPlayer.isPlaying) targetPlayer.start()
        targetPlayer.setVolume(1f, 1f)
        startProgressUpdates()
        publishState()
        updateNotification(isPlaying = true)
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val manager = audioManager ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(manager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun previous() {
        if (queueUris.isEmpty()) return
        val nextIndex = if (currentIndex <= 0) queueUris.lastIndex else currentIndex - 1
        playFromQueue(nextIndex)
    }

    private fun next() {
        if (queueUris.isEmpty()) return
        val nextIndex = if (currentIndex >= queueUris.lastIndex || currentIndex < 0) 0 else currentIndex + 1
        playFromQueue(nextIndex)
    }

    private fun setQueue(uris: List<String>, titles: List<String>, artists: List<String>, index: Int) {
        if (uris.isEmpty()) return
        queueUris = uris
        queueTitles = titles
        queueArtists = artists
        if (index >= 0) {
            currentIndex = index.coerceIn(0, uris.lastIndex)
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                publishState()
                delay(250)
            }
        }
    }

    private fun publishState() {
        val player = mediaPlayer
        val duration = player?.duration?.coerceAtLeast(0) ?: 0
        val position = player?.currentPosition?.coerceIn(0, duration) ?: 0
        updateSessionState(
            isPlaying = player?.isPlaying == true,
            positionMs = position.toLong(),
            durationMs = duration.toLong()
        )
        PlaybackStateStore.update(
            PlaybackState(
                currentUri = currentUri?.let { android.net.Uri.parse(it) },
                title = currentTitle,
                artist = currentArtist,
                isPlaying = player?.isPlaying == true,
                positionMs = position,
                durationMs = duration
            )
        )
        updateNotification(isPlaying = player?.isPlaying == true)
    }

    private fun updateNotification(isPlaying: Boolean) {
        val session = mediaSession ?: return
        val state = PlaybackStateStore.state.value
        val contentIntent = PendingIntent.getActivity(
            this,
            201,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.icon_pause,
                "Pause",
                servicePendingIntent(ACTION_TOGGLE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.icon_play_big,
                "Play",
                servicePendingIntent(ACTION_TOGGLE)
            )
        }

        val previousAction = NotificationCompat.Action(
            R.drawable.icon_previous,
            "Previous",
            servicePendingIntent(ACTION_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            R.drawable.icon_next,
            "Next",
            servicePendingIntent(ACTION_NEXT)
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_play_big)
            .setContentTitle(currentTitle.ifBlank { getString(R.string.app_name) })
            .setContentText(currentArtist.ifBlank { "Audio playback" })
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setProgress(state.durationMs.coerceAtLeast(0), state.positionMs.coerceAtLeast(0), false)
            .addAction(playPauseAction)
            .addAction(previousAction)
            .addAction(nextAction)
            .setDeleteIntent(servicePendingIntent(ACTION_STOP))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio playback",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    private fun updateSessionState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val session = mediaSession ?: return
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs,
                if (isPlaying) 1f else 0f
            )
            .build()
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        session.setPlaybackState(state)
        session.setMetadata(metadata)
    }

    companion object {
        const val ACTION_PLAY = "com.sustain.step.action.PLAY"
        const val ACTION_TOGGLE = "com.sustain.step.action.TOGGLE"
        const val ACTION_PAUSE = "com.sustain.step.action.PAUSE"
        const val ACTION_PREVIOUS = "com.sustain.step.action.PREVIOUS"
        const val ACTION_NEXT = "com.sustain.step.action.NEXT"
        const val ACTION_SET_QUEUE = "com.sustain.step.action.SET_QUEUE"
        const val ACTION_SEEK = "com.sustain.step.action.SEEK"
        const val ACTION_STOP = "com.sustain.step.action.STOP"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_QUEUE_URIS = "extra_queue_uris"
        const val EXTRA_QUEUE_TITLES = "extra_queue_titles"
        const val EXTRA_QUEUE_ARTISTS = "extra_queue_artists"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        private const val CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
