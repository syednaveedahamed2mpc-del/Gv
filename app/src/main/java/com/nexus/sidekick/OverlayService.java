package com.nexus.sidekick;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Standalone voice panel. Long-press NEVER opens the main activity. */
public class OverlayService extends Service {
    private WindowManager windows;private WindowManager.LayoutParams params;
    private LinearLayout window,panel;private VoiceOrbView orb;
    private TextView status,answer;
    private SpeechRecognizer recognizer;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable hold;
    private boolean moved,held,panelVisible=false,listening=false,retrying=false;
    private float downX,downY;private int startX,startY;
    private AssistantEngine engine;
    private int dp(int d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private GradientDrawable bg(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private void sayStatus(String message){if(status!=null)status.setText(message);}
    @Override public void onCreate(){super.onCreate();engine=NexusAgent.get(this);
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("nexus_voice","NEXUS voice assistant",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification n=new Notification.Builder(this,"nexus_voice")
            .setContentTitle("NEXUS voice assistant").setContentText("Hold the floating orb to speak")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).build();
        try{startForeground(42,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE|ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);}
        catch(Exception e){Diagnostics.record(this,"OVERLAY","Voice foreground service denied: "+e.getClass().getSimpleName());stopSelf();return;}
        if(!Settings.canDrawOverlays(this)){Diagnostics.record(this,"OVERLAY","Overlay permission missing");stopSelf();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            Diagnostics.record(this,"VOICE","Microphone permission missing; return to NEXUS setup");stopSelf();return;}
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        window=new LinearLayout(this);window.setOrientation(LinearLayout.VERTICAL);
        orb=new VoiceOrbView(this);orb.setContentDescription("NEXUS microphone, hold to talk");
        window.addView(orb,new LinearLayout.LayoutParams(dp(68),dp(68)));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(10),dp(14),dp(12));
        panel.setBackground(bg(0xf10b2139,19));panel.setVisibility(View.GONE);
        TextView title=text("✦ NEXUS LISTENING",14,0xff20e6ee);panel.addView(title);
        status=text("Hold the orb or tap TALK",12,Color.WHITE);status.setPadding(0,dp(6),0,dp(6));panel.addView(status);
        answer=text("",13,Color.WHITE);answer.setMaxLines(8);panel.addView(answer);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        Button talk=new Button(this);talk.setText("🎙 TALK");talk.setAllCaps(false);
        talk.setOnClickListener(v->startSpeech());controls.addView(talk,new LinearLayout.LayoutParams(0,dp(53),1));
        Button hide=new Button(this);hide.setText("✕");hide.setOnClickListener(v->{stopSpeech();showPanel(false);});controls.addView(hide,new LinearLayout.LayoutParams(dp(60),dp(53)));
        panel.addView(controls);
        window.addView(panel,new LinearLayout.LayoutParams(dp(280),-2));
        params=new WindowManager.LayoutParams(dp(68),dp(68),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=0;params.y=dp(250);
        orb.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getRawX();downY=event.getRawY();startX=params.x;startY=params.y;moved=false;held=false;
                    hold=()->{if(!moved){held=true;showPanel(true);startSpeech();}};
                    handler.postDelayed(hold,560);return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(Math.hypot(dx,dy)>dp(13)){moved=true;handler.removeCallbacks(hold);}
                    if(moved){params.x=startX+(int)dx;params.y=startY+(int)dy;
                        try{windows.updateViewLayout(window,params);}catch(Exception ignored){}}
                    return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(hold);
                    if(event.getActionMasked()==MotionEvent.ACTION_UP&&!moved&&!held)showPanel(!panelVisible);
                    return true;
            }return false;
        });
        try{windows.addView(window,params);Diagnostics.record(this,"OVERLAY","Voice panel attached; microphone FGS active");}
        catch(Exception e){Diagnostics.record(this,"OVERLAY","Attach failed: "+e.getClass().getSimpleName());stopSelf();}
    }
    private void showPanel(boolean show){panelVisible=show;panel.setVisibility(show?View.VISIBLE:View.GONE);
        params.width=dp(show?280:68);params.height=show?WindowManager.LayoutParams.WRAP_CONTENT:dp(68);
        if(windows!=null)try{windows.updateViewLayout(window,params);}catch(Exception ignored){}
    }
    private void stopSpeech(){listening=false;if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}}
        if(orb!=null)orb.setListening(false);}
    private void startSpeech(){
        if(listening){sayStatus("Already listening…");return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){sayStatus("Android Speech Services unavailable");Diagnostics.record(this,"VOICE","No recognizer available");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){sayStatus("Microphone permission missing. Open NEXUS setup.");return;}
        try{
            if(recognizer!=null){recognizer.destroy();recognizer=null;}
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){sayStatus("Listening • speak now");Diagnostics.record(OverlayService.this,"VOICE","Overlay microphone ready");}
                public void onBeginningOfSpeech(){sayStatus("Hearing you…");}
                public void onRmsChanged(float rms){orb.setLevel(rms);}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){sayStatus("Understanding…");}
                public void onError(int error){listening=false;orb.setListening(false);
                    Diagnostics.record(OverlayService.this,"VOICE","Overlay speech error "+error);
                    if(error==SpeechRecognizer.ERROR_SERVER_DISCONNECTED&&!retrying){
                        retrying=true;sayStatus("Speech disconnected • reconnecting…");
                        if(recognizer!=null){recognizer.destroy();recognizer=null;}
                        handler.postDelayed(()->{if(window!=null)startSpeech();},850);
                    }else{retrying=false;sayStatus("Speech error "+error+" • tap TALK to retry");}}
                public void onResults(Bundle b){listening=false;retrying=false;orb.setListening(false);
                    ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(results==null||results.isEmpty()){sayStatus("Didn't hear any words");return;}
                    String transcript=results.get(0);sayStatus("You: "+ToolGuard.truncate(transcript,65));
                    answer.setText("Processing…");Diagnostics.record(OverlayService.this,"VOICE","Transcript recognized (not logged)");
                    engine.submit(transcript,(message,done)->{
                        if(panel==null)return;
                        answer.setText(ToolGuard.truncate(message,1300));
                        if(done)sayStatus("Ready • hold orb for next request");
                    });
                }
                public void onPartialResults(Bundle b){}
                public void onEvent(int type,Bundle b){}
            });
            Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            listening=true;orb.setListening(true);sayStatus("Starting microphone…");
            Diagnostics.record(this,"VOICE","Overlay recognizer start");
            recognizer.startListening(request);
        }catch(Exception e){listening=false;orb.setListening(false);sayStatus("Voice couldn't start: "+e.getClass().getSimpleName());
            Diagnostics.record(this,"VOICE","Overlay start failed: "+e.getClass().getSimpleName());}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){if(hold!=null)handler.removeCallbacks(hold);stopSpeech();
        if(recognizer!=null)recognizer.destroy();
        if(windows!=null&&window!=null)try{windows.removeView(window);}catch(Exception ignored){}
        super.onDestroy();}
}
