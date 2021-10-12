package com.luopeng.januswebrtc;

import android.content.Intent;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;


public class WebRtcHelper implements JanusRTCInterface {

    private WebSocketChannel mWebSocketChannel;

    public static Intent addDefuleConfig(Intent intent) {
        intent.putExtra(EXTRA_LOOPBACK, false);
        intent.putExtra(EXTRA_VIDEO_CALL, true);
        intent.putExtra(EXTRA_SCREENCAPTURE, false);
        intent.putExtra(EXTRA_CAMERA2, true);
        intent.putExtra(EXTRA_VIDEO_WIDTH, 320);
        intent.putExtra(EXTRA_VIDEO_HEIGHT, 240);
        intent.putExtra(EXTRA_VIDEO_FPS, 15);
        intent.putExtra(EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
        intent.putExtra(EXTRA_VIDEO_BITRATE, 800);
        intent.putExtra(EXTRA_VIDEOCODEC, "vp9");
        intent.putExtra(EXTRA_HWCODEC_ENABLED, true);
        intent.putExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, true);
        intent.putExtra(EXTRA_FLEXFEC_ENABLED, false);
        intent.putExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false);
        intent.putExtra(EXTRA_AECDUMP_ENABLED, false);
        intent.putExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false);
        intent.putExtra(EXTRA_OPENSLES_ENABLED, false);
        intent.putExtra(EXTRA_DISABLE_BUILT_IN_AEC, false);
        intent.putExtra(EXTRA_DISABLE_BUILT_IN_AGC, false);
        intent.putExtra(EXTRA_DISABLE_BUILT_IN_NS, false);
        intent.putExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false);/////
        intent.putExtra(EXTRA_AUDIO_BITRATE, 16);
        intent.putExtra(EXTRA_AUDIOCODEC, "OPUS");
        intent.putExtra(EXTRA_DISPLAY_HUD, false);
        intent.putExtra(EXTRA_TRACING, false);
        intent.putExtra(EXTRA_ENABLE_RTCEVENTLOG, false);
        intent.putExtra(EXTRA_CMDLINE, false);
        intent.putExtra(EXTRA_RUNTIME, 0);
        intent.putExtra(EXTRA_USE_LEGACY_AUDIO_DEVICE, false);
        boolean dataChannelEnabled = false;
        intent.putExtra(EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);

        if (dataChannelEnabled) {
            intent.putExtra(EXTRA_ORDERED, true);
            intent.putExtra(EXTRA_NEGOTIATED, false);
            intent.putExtra(EXTRA_MAX_RETRANSMITS_MS, -1);
            intent.putExtra(EXTRA_MAX_RETRANSMITS, -1);
            intent.putExtra(EXTRA_ID, -1);
            intent.putExtra(EXTRA_PROTOCOL, "");
        }
        return intent;
    }

    public static final String EXTRA_SERVERADDR = "org.appspot.apprtc.ROOMURL";
    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_USERID = "org.appspot.apprtc.USERID";
    public static final String EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS";
    public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
    public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
    public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
    public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED = "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
    public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED = "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED = "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE";
    public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF = "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL";
    public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
    public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
    public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT = "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
    public static final String EXTRA_USE_VALUES_FROM_INTENT = "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
    public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
    public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
    public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
    public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
    public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
    public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
    public static final String EXTRA_ID = "org.appspot.apprtc.ID";
    public static final String EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG";
    public static final String EXTRA_USE_LEGACY_AUDIO_DEVICE = "org.appspot.apprtc.USE_LEGACY_AUDIO_DEVICE";

    public static final String EXTRA_NO_AUDIO_PROCESSING = "org.appspot.apprtc.NO_AUDIO_PROCESSING";
    public static final String EXTRA_USE_OPEN_SLES = "org.appspot.apprtc.USE_OPEN_SLES";
    public static final String EXTRA_SESSIONID = "org.appspot.apprtc.SESSIONID";
    public static final String EXTRA_TAGERTID = "org.appspot.apprtc.TAGERTID";


    //使用volatile关键字保其可见性
    volatile private static WebRtcHelper instance = null;


    private WebRtcHelper() {

    }

    public static WebRtcHelper getInstance() {
        if (instance == null) //懒汉式 //创建实例之前可能会有一些准备性的耗时工作
            synchronized (WebRtcHelper.class) {
                if (instance == null) instance = new WebRtcHelper();//二次检查
            }
        return instance;
    }

    public String iceServer = "stun:stun.freeswitch.org";

    public void connect(String url, String iceServer, long roomId, String displayName, int maxPublishersNum, JanusRTCInterface events) {
        this.iceServer = iceServer;
        this.mJanusRTCEvents = events;
        mWebSocketChannel = new WebSocketChannel();
        mWebSocketChannel.initConnection(url, roomId, displayName, maxPublishersNum);
        mWebSocketChannel.setDelegate(this);
    }


    private JanusRTCInterface mJanusRTCEvents;

    public void addJanusEvents(JanusRTCInterface events) {
        this.mJanusRTCEvents = events;
    }


    public void joinRoom() {
        if (mWebSocketChannel != null) mWebSocketChannel.joinRoom();
    }

    public void disConnect() {
        if (mWebSocketChannel != null) mWebSocketChannel.disconnectFromServer();
    }

    @Override
    public void onPublisherJoined(BigInteger handleId) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onPublisherJoined(handleId);
    }


    @Override
    public void onPublisherRemoteJsep(BigInteger handleId, JSONObject jsep) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onPublisherRemoteJsep(handleId, jsep);

    }

    @Override
    public void subscriberHandleRemoteJsep(BigInteger handleId, JSONObject jsep) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.subscriberHandleRemoteJsep(handleId, jsep);

    }

    @Override
    public void onLeaving(BigInteger handleId) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onLeaving(handleId);
    }

    @Override
    public void onError(String message) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onError(message);
    }

    @Override
    public void onMessage(String message) {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onMessage(message);
    }

    @Override
    public void onRoomReady() {
        if (mJanusRTCEvents != null) mJanusRTCEvents.onRoomReady();
    }


    public void publisherCreateOffer(BigInteger handleId, SessionDescription sdp) {
        if (mWebSocketChannel != null) mWebSocketChannel.publisherCreateOffer(handleId, sdp);
    }

    public void subscriberCreateAnswer(BigInteger handleId, SessionDescription sdp) {
        if (mWebSocketChannel != null) mWebSocketChannel.subscriberCreateAnswer(handleId, sdp);
    }

    public void trickleCandidate(BigInteger handleId, IceCandidate candidate) {
        if (mWebSocketChannel != null) mWebSocketChannel.trickleCandidate(handleId, candidate);
    }

    public void trickleCandidateComplete(BigInteger handleId) {
        if (mWebSocketChannel != null) mWebSocketChannel.trickleCandidateComplete(handleId);
    }
}
