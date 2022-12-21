package com.skt.nugu.sdk.platform.android.ux.template.view.media.playlist

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.audioplayer.playlist.Playlist
import com.skt.nugu.sdk.agent.display.DisplayInterface

interface PlaylistRenderer {
    fun showPlaylist(): Boolean

    fun hidePlaylist(): Boolean

    fun isPlaylistVisible(): Boolean
}

interface PlaylistStateListener {
    fun onPlaylistHidden() {}

    fun onPlaylistEditModeChanged(editMode: Boolean) {}
}

interface PlaylistDataListener {
    fun onSetPlaylist(playlist: Playlist) {}

    fun onUpdatePlaylist(changes: JsonObject, updated: Playlist) {}
}

interface PlaylistEventListener {
    fun setElementSelected(
        token: String,
        postback: String?,
        callback: DisplayInterface.OnElementSelectedCallback?
    ) {
    }

    fun playlistModified(deletedTokens: List<String>, tokens: List<String>) {}
}