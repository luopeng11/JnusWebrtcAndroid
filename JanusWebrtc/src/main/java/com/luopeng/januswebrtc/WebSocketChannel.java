package com.luopeng.januswebrtc;

import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class WebSocketChannel {
    private static final String TAG = "WebSocketChannel";

    private WebSocket mWebSocket;
    private ConcurrentHashMap<String, JanusTransaction> transactions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> handles = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> feeds = new ConcurrentHashMap<>();
    private Handler mHandler;
    private BigInteger mSessionId;
    private JanusRTCInterface delegate;
    private BigInteger mPublishHandleId;

    public WebSocketChannel() {
        mHandler = new Handler();
    }

    private int mMaxPublishersNum;
    private long mRoomId;
    private String mDisplayName;

    public void initConnection(String url) {
        initConnection(url, 1234, "Android Webrtc", 3);
    }

    public void initConnection(String url, long roomId, String displayName, int maxPublishersNum) {
        this.mRoomId = roomId;
        this.mMaxPublishersNum = maxPublishersNum;
        this.mDisplayName = displayName;
        OkHttpClient httpClient = new OkHttpClient.Builder()
               // .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Interceptor.Chain chain) throws IOException {
                        Request.Builder builder = chain.request().newBuilder();
                        builder.addHeader("Sec-WebSocket-Protocol", "janus-protocol");
                        return chain.proceed(builder.build());
                    }
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).build();
        mWebSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.e(TAG, "onOpen");
                createSession();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.e(TAG, "onMessage");
                WebSocketChannel.this.onMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.e(TAG, "onClosing");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "onFailure" + t.toString());
            }
        });
    }

    private void onMessage(String message) {
        Log.e(TAG, "onMessage:" + message);
        try {
            JSONObject jo = new JSONObject(message);
            String janus = jo.optString("janus");
            if (janus.equals("success")) {
                String transaction = jo.optString("transaction");
                JanusTransaction jt = transactions.get(transaction);
                if (jt.success != null) {
                    jt.success.success(jo);
                }
                transactions.remove(transaction);
            } else if (janus.equals("error")) {
                String transaction = jo.optString("transaction");
                JanusTransaction jt = transactions.get(transaction);
                if (jt.error != null) {
                    jt.error.error(jo);
                }
                transactions.remove(transaction);
            } else if (janus.equals("ack")) {
                Log.e(TAG, "Just an ack");
            } else {
                JanusHandle handle = handles.get(new BigInteger(jo.optString("sender")));
                if (handle == null) {
                    Log.e(TAG, "missing handle");
                } else if (janus.equals("event")) {
                    JSONObject plugin = jo.optJSONObject("plugindata").optJSONObject("data");
                    if (plugin.optString("videoroom").equals("joined")) {
                        handle.onJoined.onJoined(handle);
                    }

                    JSONArray publishers = plugin.optJSONArray("publishers");
                    if (publishers != null && publishers.length() > 0) {
                        for (int i = 0, size = publishers.length(); i <= size - 1; i++) {
                            JSONObject publisher = publishers.optJSONObject(i);
                            BigInteger feed = new BigInteger(publisher.optString("id"));
                            String display = publisher.optString("display");
                            subscriberCreateHandle(feed, display);
                        }
                    }

                    String leaving = plugin.optString("leaving");
                    if (!TextUtils.isEmpty(leaving)) {
                        JanusHandle jhandle = feeds.get(new BigInteger(leaving));
                        jhandle.onLeaving.onJoined(jhandle);
                    }

                    JSONObject jsep = jo.optJSONObject("jsep");
                    if (jsep != null) {
                        handle.onRemoteJsep.onRemoteJsep(handle, jsep);
                    }

                } else if (janus.equals("detached")) {
                    handle.onLeaving.onJoined(handle);
                }else if (janus.equals("media")) {
                    String type = jo.optString("type");
                    boolean receiving = jo.optBoolean("receiving");
                    Log.e(TAG, "media type:" + type + "  receiving:" + receiving);
                    if (!receiving) delegate.onError(type + "中断！");
                } else if (janus.equals("slowlink")) {
                    String media = jo.optString("media");
                    Log.e(TAG, "media type:" + media + "  slowlink");
                    delegate.onMessage("您当前" + media + "通话网络质量差！");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void createSession() {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                mSessionId = new BigInteger(jo.optJSONObject("data").optString("id"));
                mHandler.post(fireKeepAlive);
                publisherCreateHandle();
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "create");
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    private void publisherCreateHandle() {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                mPublishHandleId = new BigInteger(jo.optJSONObject("data").optString("id"));
                checkRoom(mPublishHandleId);
//                JanusHandle janusHandle = new JanusHandle();
//                janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
//                janusHandle.onJoined = new OnJoined() {
//                    @Override
//                    public void onJoined(JanusHandle jh) {
//                        delegate.onPublisherJoined(jh.handleId);
//                    }
//                };
//                janusHandle.onRemoteJsep = new OnRemoteJsep() {
//                    @Override
//                    public void onRemoteJsep(JanusHandle jh, JSONObject jsep) {
//                        delegate.onPublisherRemoteJsep(jh.handleId, jsep);
//                    }
//                };
//                handles.put(janusHandle.handleId, janusHandle);
//                publisherJoinRoom(janusHandle);
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    private void checkRoom(BigInteger handleId) {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                if (jo.optJSONObject("plugindata").optJSONObject("data").optBoolean("exists")) {
                    if (delegate!=null)delegate.onRoomReady();
                } else {
                    createRoome(handleId);
                }
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
                delegate.onError("获取聊天室状态出错！");
            }
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "exists");
            body.putOpt("room", mRoomId);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    private void createRoome(BigInteger handleId) {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                if (delegate!=null)delegate.onRoomReady();
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
                delegate.onError("聊天室创建失败！");
            }
        };
        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "create");
            body.putOpt("room", mRoomId);//唯一数字ID，可选，如果缺少则由插件选择
            body.putOpt("permanent", false);//房间是否应保存在配置文件中，默认=false
            body.putOpt("description ", "Room " + mRoomId);//房间描述
            //body.putOpt("secret", "");//编辑/销毁房间所需的密码，可选
            //body.putOpt("pin", "");//加入房间所需的密码，可选
            body.putOpt("is_private", false);//是否私人聊天室 列表请求时 不会出现私人房间，默认值为false

            body.putOpt("publishers", mMaxPublishersNum);//并发发送者的最大数量 例如，视频会议为6，网络研讨会为 1，默认值 = 3
            body.putOpt("bitrate", 300000);//发件人的最大视频比特率 例如，128000
            body.putOpt("bitrate_cap", true);//true|false，上述上限是否应作为发布者动态比特率变化的限制，默认=false
            body.putOpt("fir_freq", 20);//每 fir_freq 秒向发布者发送 FIR>（0=禁用）

            //opus|g722 |pcmu|pcma|isac32|isac16（音频编解码器强制发布商，默认=opus 可以是逗号分隔的列表，按优先顺序排列，例如 opus,pcmu
            body.putOpt("audiocodec", "opus,isac32,isac16");

            // vp8|vp9|h264|av1|h265（视频编解码器强制发布商，default=vp8 可以是一个逗号分隔的列表，按照偏好的顺序，例如，vp9,vp8,h264)
            body.putOpt("videocodec", "vp9,vp8");
            // body.putOpt("vp9_profile", "2");//VP9 特定的配置文件（例如，“2”代表“profile-id=2”）
            // body.putOpt("h264_profile", "42e01f");//首选的 H.264 特定配置文件（例如，“42e01f”代表“profile-level-id=42e01f”）
            body.putOpt("opus_fec", false);//是否必须协商带内 FEC；仅适用于 Opus，默认 = false
            body.putOpt("video_svc", false);//是否必须启用 SVC 支持；仅适用于 VP9，默认=false

            body.putOpt("audiolevel_ext", true);//是否必须为新发布商协商/使用ssrc-audio-level RTP 扩展，默认=true
            body.putOpt("audiolevel_event", false);//true|false (是否向其他用户发出事件，默认=false)
            body.putOpt("audio_active_packets", 100);//音频级别的数据包数量，默认= 100，2秒
            body.putOpt("audio_level_average", 25);//音频电平的平均值，127=静音，0='太响'，默认=25
            body.putOpt("videoorient_ext", true);//是否必须为新发布商协商/使用视频方向RTP扩展，默认=true
            body.putOpt("playoutdelay_ext", true);//是否必须为新发布商协商/使用播放延迟 RTP 扩展，默认值=true
            body.putOpt("transport_wide_cc_ext", true);//传输宽 CC RTP 扩展是否必须协商/使用或不适用于新发布者，默认=true
            body.putOpt("record", false);//是否要录制这个房间，默认=false
            //body.putOpt("rec_dir", "");//录像存储文件夹，启用时
            body.putOpt("lock_record ", false);//是否只能在提供密钥的情况下开始/停止录制，或使用全局 enable_recording 请求，默认值=false

            //可选，是否在新参与者加入房间时通知所有参与者。Videoroom 插件设计为仅通知新提要（发布者），启用此功能可能会导致额外的通知流量。
            // 此标志在启用 require_pvtid 时特别有用供管理员管理只收听的参与者。default=false
            body.putOpt("notify_joining", false);
            body.putOpt("require_e2ee ", false); //是否所有参与者都需要使用端到端媒体加密来发布和订阅，例如，通过可插入流；default=false


            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    public void joinRoom() {
        JanusHandle janusHandle = new JanusHandle();
        janusHandle.handleId = mPublishHandleId;
        janusHandle.onJoined = new OnJoined() {
            @Override
            public void onJoined(JanusHandle jh) {
                delegate.onPublisherJoined(jh.handleId);
            }
        };
        janusHandle.onRemoteJsep = new OnRemoteJsep() {
            @Override
            public void onRemoteJsep(JanusHandle jh, JSONObject jsep) {
                delegate.onPublisherRemoteJsep(jh.handleId, jsep);
            }
        };
        handles.put(janusHandle.handleId, janusHandle);
        publisherJoinRoom(janusHandle);
    }

    private void publisherJoinRoom(JanusHandle handle) {
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", mRoomId);
            body.putOpt("ptype", "publisher");
            body.putOpt("display", mDisplayName);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString(12));
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    public void publisherCreateOffer(final BigInteger handleId, final SessionDescription sdp) {
        JSONObject publish = new JSONObject();
        JSONObject jsep = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            publish.putOpt("request", "configure");
            publish.putOpt("audio", true);
            publish.putOpt("video", true);

            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);

            message.putOpt("janus", "message");
            message.putOpt("body", publish);
            message.putOpt("jsep", jsep);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(message.toString());
    }

    public void subscriberCreateAnswer(final BigInteger handleId, final SessionDescription sdp) {
        JSONObject body = new JSONObject();
        JSONObject jsep = new JSONObject();
        JSONObject message = new JSONObject();

        try {
            body.putOpt("request", "start");
            body.putOpt("room", mRoomId);

            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);
            message.putOpt("janus", "message");
            message.putOpt("body", body);
            message.putOpt("jsep", jsep);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
            Log.e(TAG, "-------------" + message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        onSend(message.toString());
    }

    public void trickleCandidate(final BigInteger handleId, final IceCandidate iceCandidate) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("candidate", iceCandidate.sdp);
            candidate.putOpt("sdpMid", iceCandidate.sdpMid);
            candidate.putOpt("sdpMLineIndex", iceCandidate.sdpMLineIndex);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(message.toString());
    }

    public void trickleCandidateComplete(final BigInteger handleId) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("completed", true);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", mSessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void subscriberCreateHandle(final BigInteger feed, final String display) {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                JanusHandle janusHandle = new JanusHandle();
                janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
                janusHandle.feedId = feed;
                janusHandle.display = display;
                janusHandle.onRemoteJsep = new OnRemoteJsep() {
                    @Override
                    public void onRemoteJsep(JanusHandle jh, JSONObject jsep) {
                        delegate.subscriberHandleRemoteJsep(jh.handleId, jsep);
                    }
                };
                janusHandle.onLeaving = new OnJoined() {
                    @Override
                    public void onJoined(JanusHandle jh) {
                        subscriberOnLeaving(jh);
                    }
                };
                handles.put(janusHandle.handleId, janusHandle);
                feeds.put(janusHandle.feedId, janusHandle);
                subscriberJoinRoom(janusHandle);
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };

        transactions.put(transaction, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transaction);
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        onSend(msg.toString());
    }

    private void subscriberJoinRoom(JanusHandle handle) {

        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", mRoomId);
//            body.putOpt("ptype", "listener");
            body.putOpt("ptype", "subscriber");
            body.putOpt("feed", handle.feedId);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", randomString(12));
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    private void subscriberOnLeaving(final JanusHandle handle) {
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                delegate.onLeaving(handle.handleId);
                handles.remove(handle.handleId);
                feeds.remove(handle.feedId);
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };

        transactions.put(transaction, jt);

        JSONObject jo = new JSONObject();
        try {
            jo.putOpt("janus", "detach");
            jo.putOpt("transaction", transaction);
            jo.putOpt("session_id", mSessionId);
            jo.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(jo.toString());
    }

    private void keepAlive() {
        String transaction = randomString(12);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "keepalive");
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("transaction", transaction);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            mHandler.postDelayed(fireKeepAlive, 30000);
        }
    };

    public void setDelegate(JanusRTCInterface delegate) {
        this.delegate = delegate;
    }

    private String randomString(Integer length) {
        final String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Random rnd = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(str.charAt(rnd.nextInt(str.length())));
        }
        return sb.toString();
    }

    public void disconnectFromServer(boolean destroyRoom) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (destroyRoom)destroyRoom();
                else disconnect();
                mHandler.removeCallbacksAndMessages(null);
            }
        });
    }

    private void disconnect() {
        destroy();

        transactions.clear();
        handles.clear();
        feeds.clear();

        if (mWebSocket != null) {
            mWebSocket.cancel();
        }
    }

    private void destroyRoom() {
        if (mSessionId == BigInteger.ZERO) {
            Log.w(TAG, "destroy() for sessionid 0");
            return;
        }
        String transaction = randomString(12);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transaction;
        jt.success = new TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                if (delegate != null) delegate.onMessage("destroyRoom success!");
                disconnect();
            }
        };
        jt.error = new TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
                if (delegate != null) delegate.onMessage("destroyRoom error!");
            }
        };
        transactions.put(transaction, jt);

        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "destroy");
            body.putOpt("permanent", true);
            body.putOpt("room", mRoomId);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction",transaction);
            msg.putOpt("session_id", mSessionId);
            msg.putOpt("handle_id", mPublishHandleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
    }


    private void destroy() {
        if (mSessionId == BigInteger.ZERO) {
            Log.w(TAG, "destroy() for sessionid 0");
            return;
        }


        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "destroy");
            msg.putOpt("transaction", randomString(12));
            msg.putOpt("session_id", mSessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        onSend(msg.toString());
        mSessionId = BigInteger.ZERO;
    }

    private void onSend(String message) {
        Log.e(TAG, "onSend:" + message);
        mWebSocket.send(message);
    }
}
