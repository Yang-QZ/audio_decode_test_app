package com.example.audiodecodetestapp.audio

import android.content.Context
import com.example.audiodecodetestapp.model.PlaybackEngine

object EnginePlayerFactory {
    fun create(engine: PlaybackEngine, context: Context): EnginePlayer = when (engine) {
        PlaybackEngine.AUDIO_TRACK -> AudioTrackEnginePlayer(context)
        PlaybackEngine.MEDIA_PLAYER -> MediaPlayerEnginePlayer(context)
        PlaybackEngine.MEDIA3 -> Media3EnginePlayer(context)
        PlaybackEngine.SOUND_POOL -> SoundPoolEnginePlayer(context)
        PlaybackEngine.AAUDIO -> AAudioEnginePlayer(context)
    }
}
