package com.liuxi.cince;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    // Uygulama içeriği güvenli (https) bir sanal kökenden sunulur; mikrofon (getUserMedia,
    // yalnızca alt tarayıcı motoru için) bu köken üzerinden çalışır. Konuşma alıştırması ise
    // WebView'i atlayıp doğrudan Android'in kendi SpeechRecognizer'ını kullanır.
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_AUDIO = 77;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private PermissionRequest pendingWebPerm;
    private SpeechRecognizer asr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setKeepScreenOn(true);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
                    try {
                        InputStream in = getAssets().open(path.substring(1));
                        return new WebResourceResponse(mime(path), "UTF-8", in);
                    } catch (IOException e) {
                        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, req);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost());
            }
        });
        // WebView'in kendi getUserMedia'sı (yedek yol) için izin köprüsü.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean fromApp = HOST.equals(request.getOrigin().getHost());
                    boolean wantsAudio = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
                    }
                    if (!fromApp || !wantsAudio) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingWebPerm = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                    }
                });
            }
        });
        tts = new TextToSpeech(this, this);
        web.addJavascriptInterface(new Bridge(), "AndroidTTS");
        web.addJavascriptInterface(new AsrBridge(), "AndroidASR");
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            asr = SpeechRecognizer.createSpeechRecognizer(this);
        }
        web.loadUrl("https://" + HOST + "/index.html");
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean granted = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_AUDIO && pendingWebPerm != null) {
            if (granted) {
                pendingWebPerm.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingWebPerm.deny();
            }
            pendingWebPerm = null;
            return;
        }
        // Konuşma tanıma izin isteği: sonucu JS'e bildir (window.__recPerm).
        web.evaluateJavascript("window.__recPerm && window.__recPerm(" + granted + ")", null);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false;
            return;
        }
        int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { ttsDone(); }
            @Override public void onError(String id) { ttsDone(); }
        });
        if (!ttsReady) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Çince ses verisi yok. Ayarlar > Metin okuma > Google TTS > Çince ses indir.",
                    Toast.LENGTH_LONG).show());
        }
    }

    private void ttsDone() {
        web.post(() -> web.evaluateJavascript("window.__ttsDone && window.__ttsDone()", null));
    }

    private class Bridge {
        @JavascriptInterface
        public boolean speak(final String text, final float rate, final float pitch) {
            if (!ttsReady) return false;
            runOnUiThread(() -> {
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
            });
            return true;
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> tts.stop());
        }

        @JavascriptInterface
        public boolean isReady() {
            return ttsReady;
        }
    }

    // Cihaz üzerinde (Android'in kendi) konuşma tanıma köprüsü. Ses hiçbir yere gönderilmez
    // veya kalıcı kaydedilmez; yalnızca tanınan metin JS'e döner. Puan üretmez.
    // Yalnızca kullanıcı "Söyle" düğmesine basınca (JS: AndroidASR.start) çağrılır.
    private class AsrBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return asr != null;
        }

        @JavascriptInterface
        public boolean hasPermission() {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO));
        }

        @JavascriptInterface
        public void start(final String locale) {
            runOnUiThread(() -> beginListening(locale, true));
        }

        // preferOffline=true: önce cihaz üzerinde (çevrimdışı) dener. Telefonda Çince
        // çevrimdışı paket kurulu değilse (hata 12/13) KULLANICIYA HATA GÖSTERMEDEN,
        // sessizce ve otomatik olarak çevrimiçi tanımayla bir kez daha dener. Başka bir
        // hata (izin, ağ, meşgul, vb.) doğrudan JS'e iletilir.
        private void beginListening(final String locale, final boolean preferOffline) {
            if (asr == null) {
                emit("error", "5");
                return;
            }
            asr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }

                @Override
                public void onError(int error) {
                    boolean offlinePackMissing = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                            || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE;
                    if (preferOffline && offlinePackMissing) {
                        runOnUiThread(() -> beginListening(locale, false));
                        return;
                    }
                    emit("error", String.valueOf(error));
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    emit("final", (list != null && !list.isEmpty()) ? list.get(0) : "");
                }

                @Override
                public void onPartialResults(Bundle partial) {
                    ArrayList<String> list = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) emit("partial", list.get(0));
                }

                @Override public void onEvent(int e, Bundle b) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale == null || locale.isEmpty() ? "zh-CN" : locale);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            if (preferOffline) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            try {
                asr.startListening(intent);
            } catch (Exception e) {
                emit("error", "5");
            }
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                if (asr != null) asr.stopListening();
            });
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(() -> {
                if (asr != null) asr.cancel();
            });
        }

        private void emit(final String evt, final String payload) {
            final String js = "window.__asr && window.__asr(" + JSONObject.quote(evt) + "," + JSONObject.quote(payload) + ")";
            web.post(() -> web.evaluateJavascript(js, null));
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false", value -> {
            if (!"true".equals(value)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onPause() {
        // Arka plana geçince mikrofonu/dinlemeyi kapat.
        web.evaluateJavascript("window.__pause && window.__pause()", null);
        if (asr != null) asr.cancel();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (asr != null) {
            asr.destroy();
        }
        super.onDestroy();
    }
}
