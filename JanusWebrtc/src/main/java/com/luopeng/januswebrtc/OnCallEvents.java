package com.luopeng.januswebrtc;

import org.webrtc.RendererCommon;

public interface OnCallEvents {
    void onCallHangUp();
    void onCameraSwitch();
    void onVideoScalingSwitch(RendererCommon.ScalingType scalingType);
    void onCaptureFormatChange(int width, int height, int framerate);
    boolean onToggleMic();
}
