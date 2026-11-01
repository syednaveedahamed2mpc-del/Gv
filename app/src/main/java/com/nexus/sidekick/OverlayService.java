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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** A draggable orb with an independent, optional speech panel. No activity is launched on long press. */
public final class OverlayService extends Service implements NexusAgent.Listener {
    private WindowManager manager;private WindowManager.LayoutParams orbParams,panelParams;
    private VoiceOrbView orb;private LinearLayout panel;
    private TextView status,heard,answer;private Button approve,deny,followupButton;
    private SpeechRecognizer recognizer;private TextToSpeech tts;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Runnable hold,approveTask,denyTask;
    private boolean panelShown=false,listening=false,dragged=false,longPressed=false,session=false,followups=true;
    private int followupCount=0;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int fill){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(18));d.setStroke(dp(1),0xff2587a0);return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button btn(String s,Runnable run){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1b637a));b.setOnClickListener(v->run.run());return b;}
    @Override public void onCreate(){super.onCreate();
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("nexus_voice","NEXUS voice assistant",NotificationManager.IMPORTANCE_LOW));
        Intent openSettings=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification notification=new Notification.Builder(this,"nexus_voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("NEXUS Sidekick")
            .setContentText("Hold the floating orb to talk").setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,0,openSettings,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build();
        try{startForeground(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE|ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);}
        catch(Exception e){Diagnostics.record(this,"OVERLAY","Mic foreground service blocked "+e.getClass().getSimpleName());stopSelf();return;}
        if(!Settings.canDrawOverlays(this)||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){Diagnostics.record(this,"OVERLAY","Overlay or mic permission missing");stopSelf();return;}
        tts=new TextToSpeech(this,code->Diagnostics.record(this,"TTS",code==TextToSpeech.SUCCESS?"Ready":"Engine unavailable"));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){ui.post(()->{
                if(session&&followups&&followupCount<4){followupCount++;ui.postDelayed(()->startListening(),450);}
                else{session=false;if(status!=null)status.setText("Ready • hold orb to talk");}
            });}
            @Override public void onError(String id){ui.post(()->{session=false;if(status!=null)status.setText("Voice playback unavailable • use TALK");});}
        });
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        createPanel();createOrb();
        Diagnostics.record(this,"OVERLAY","Speech panel ready • no activity redirect");
    }
    private void createPanel(){
        panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(14),dp(12),dp(14),dp(12));panel.setBackground(bg(0xff0b2538));
        panel.addView(label("✦ NEXUS • LIVE VOICE",17,0xff20e6ee));
        status=label("Hold orb or press TALK",12,0xffb5d5de);panel.addView(status);
        heard=label("",13,Color.WHITE);heard.setMaxLines(2);panel.addView(heard);
        ScrollView reader=new ScrollView(this);answer=label("I'm here. What should I do?",14,Color.WHITE);reader.addView(answer);panel.addView(reader,new LinearLayout.LayoutParams(-1,dp(112)));
        LinearLayout row=new LinearLayout(this);row.setOrientation(0);
        row.addView(btn("🎙 TALK",this::startListening),new LinearLayout.LayoutParams(0,dp(45),1));
        row.addView(btn("■ STOP",this::stopSession),new LinearLayout.LayoutParams(0,dp(45),1));
        row.addView(btn("× HIDE",this::hidePanel),new LinearLayout.LayoutParams(0,dp(45),1));panel.addView(row);
        followupButton=btn("Follow-up listening: ON",()->{followups=!followups;followupButton.setText(followups?"Follow-up listening: ON":"Follow-up listening: OFF");});panel.addView(followupButton);
        panel.addView(btn("⚙ Settings & skills",()->{
            Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
        }));
        approve=btn("✓ APPROVE ONE ACTION",()->{Runnable r=approveTask;clearApproval();if(r!=null)r.run();});
        deny=btn("× DENY",()->{Runnable r=denyTask;clearApproval();if(r!=null)r.run();});
        approve.setVisibility(View.GONE);deny.setVisibility(View.GONE);panel.addView(approve);panel.addView(deny);
        panelParams=new WindowManager.LayoutParams(Math.min(dp(345),getResources().getDisplayMetrics().widthPixels-dp(14)),dp(395),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        panelParams.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;panelParams.y=dp(85);
    }
    private void createOrb(){
        orb=new VoiceOrbView(this);orb.setContentDescription("NEXUS assistant: hold to speak, tap for panel");
        orbParams=new WindowManager.LayoutParams(dp(69),dp(69),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        orbParams.gravity=Gravity.TOP|Gravity.START;orbParams.x=dp(4);orbParams.y=dp(230);
        final float threshold=ViewConfiguration.get(this).getScaledTouchSlop()*2f;
        orb.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;
            @Override public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    x=e.getRawX();y=e.getRawY();ox=orbParams.x;oy=orbParams.y;dragged=false;longPressed=false;
                    hold=()->{if(!dragged){longPressed=true;showPanel();startListening();}};
                    ui.postDelayed(hold,520);return true;
                case MotionEvent.ACTION_MOVE:
                    if(!dragged&&Math.hypot(e.getRawX()-x,e.getRawY()-y)>threshold){dragged=true;ui.removeCallbacks(hold);}
                    if(dragged){orbParams.x=ox+(int)(e.getRawX()-x);orbParams.y=oy+(int)(e.getRawY()-y);try{manager.updateViewLayout(orb,orbParams);}catch(Exception ignored){}}
                    return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(hold);
                    if(e.getActionMasked()==MotionEvent.ACTION_UP&&!dragged&&!longPressed)showPanel();return true;
            }return false;}
        });
        try{manager.addView(orb,orbParams);}catch(Exception e){Diagnostics.record(this,"OVERLAY","Orb attach "+e.getClass().getSimpleName());stopSelf();}
    }
    private void showPanel(){if(panelShown)return;try{manager.addView(panel,panelParams);panelShown=true;}catch(Exception e){Diagnostics.record(this,"OVERLAY","Panel attach "+e.getClass().getSimpleName());}}
    private void hidePanel(){stopSession();if(panelShown){try{manager.removeView(panel);}catch(Exception ignored){}panelShown=false;}}
    private void clearApproval(){approveTask=null;denyTask=null;approve.setVisibility(View.GONE);deny.setVisibility(View.GONE);}
    private void stopSession(){session=false;listening=false;followupCount=0;if(orb!=null)orb.setListening(false);
        if(recognizer!=null)try{recognizer.cancel();}catch(Exception ignored){}if(tts!=null)tts.stop();
        if(status!=null)status.setText("Stopped • hold orb to talk");}
    private void startListening(){showPanel();
        if(listening){status.setText("Already listening…");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){status.setText("Mic permission needed in NEXUS settings.");return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("Android speech service isn't available.");Diagnostics.record(this,"VOICE","No speech provider");return;}
        try{
            if(tts!=null)tts.stop();if(recognizer!=null)recognizer.destroy();
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle b){status.setText("Listening… speak now");}
                @Override public void onBeginningOfSpeech(){status.setText("Hearing you…");}
                @Override public void onRmsChanged(float level){orb.setLevel(level);}
                @Override public void onBufferReceived(byte[] b){}
                @Override public void onEndOfSpeech(){status.setText("Understanding speech…");}
                @Override public void onError(int e){listening=false;session=false;orb.setListening(false);status.setText("Speech error "+e+" • tap TALK to retry");Diagnostics.record(OverlayService.this,"VOICE","Recognizer error "+e);}
                @Override public void onResults(Bundle b){listening=false;orb.setListening(false);
                    ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(results==null||results.isEmpty()){session=false;status.setText("No speech captured. Try again.");return;}
                    heard.setText("You: "+results.get(0));Diagnostics.record(OverlayService.this,"VOICE","Result received");runVoice(results.get(0));
                }
                @Override public void onPartialResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words!=null&&!words.isEmpty())heard.setText("You: "+words.get(0));}
                @Override public void onEvent(int type,Bundle b){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            listening=true;session=true;orb.setListening(true);status.setText("Starting microphone…");Diagnostics.record(this,"VOICE","Started in overlay service");recognizer.startListening(intent);
        }catch(Exception e){listening=false;session=false;orb.setListening(false);status.setText("Android microphone error: "+e.getClass().getSimpleName());Diagnostics.record(this,"VOICE","Startup failed "+e.getClass().getSimpleName());}
    }
    private void runVoice(String request){
        CommandRouter.Command cmd=CommandRouter.parse(request);
        if(cmd.type==CommandRouter.Type.CHAT||(cmd.type==CommandRouter.Type.OPEN_APP&&(request.toLowerCase(Locale.ROOT).contains(" and ")||request.toLowerCase(Locale.ROOT).contains(" then ")))){
            NexusAgent.get(this).ask(request,this);return;
        }
        try{
            String tool=null,value=cmd.value;
            switch(cmd.type){case YOUTUBE_SEARCH:tool="youtube_search";break;case GOOGLE_SEARCH:tool="web_search";break;
                case OPEN_APP:tool="open_app";break;case OPEN_SETTINGS:tool="open_app";value="settings";break;case OPEN_CAMERA:tool="open_app";value="camera";break;
                default:break;}
            if(tool==null){status.setText("What would you like me to do?");session=false;return;}
            JSONObject args=new JSONObject().put(tool.equals("open_app")?"app":"query",value);
            reply(SkillExecutor.execute(this,tool,args,true).message);
        }catch(Exception e){session=false;status.setText("Action failed: "+e.getClass().getSimpleName());}
    }
    @Override public void status(String message){ui.post(()->{showPanel();status.setText(message);});}
    @Override public void reply(String message){ui.post(()->{
        showPanel();answer.setText(message);status.setText("NEXUS replied");
        if(tts==null){session=false;return;}
        String spoken=message.length()>700?message.substring(0,700):message;
        int rc=tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"nexus_reply_"+System.currentTimeMillis());
        if(rc==TextToSpeech.ERROR){session=false;status.setText("Text shown; voice playback unavailable.");}
    });}
    @Override public void approval(String description,Runnable yes,Runnable no){ui.post(()->{
        showPanel();session=false;status.setText("Confirm this specific screen action");answer.setText(description);
        approveTask=yes;denyTask=no;approve.setVisibility(View.VISIBLE);deny.setVisibility(View.VISIBLE);
    });}
    @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopSession();if(hold!=null)ui.removeCallbacks(hold);
        if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();
        if(manager!=null){if(panelShown)try{manager.removeView(panel);}catch(Exception ignored){}if(orb!=null)try{manager.removeView(orb);}catch(Exception ignored){}}
        Diagnostics.record(this,"OVERLAY","Service stopped");super.onDestroy();}
}
