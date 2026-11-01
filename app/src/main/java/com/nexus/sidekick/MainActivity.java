package com.nexus.sidekick;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** One-time setup and optional text chat. The floating panel handles voice without navigation. */
public class MainActivity extends Activity {
    private static final int BG=0xff081526,PANEL=0xff11243a,CYAN=0xff21e5ee;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout layout;private TextView state,response,keyStatus,logText,accessStatus;
    private EditText prompt,keyInput;private VoiceOrbView orb;
    private SpeechRecognizer recognizer;private boolean listening=false,showingLog=false;
    private AssistantEngine engine;
    private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(sp);return v;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(17),dp(15),dp(17),dp(15));c.setBackground(bg(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(13));layout.addView(c,p);return c;}
    private void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void button(LinearLayout l,String name,Runnable work){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->work.run());add(l,b);}
    @Override public void onCreate(Bundle stateBundle){super.onCreate(stateBundle);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        engine=AssistantEngine.get(this);migrateOldKey();
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(17),dp(30),dp(17),dp(30));
        scroll.addView(layout);setContentView(scroll);
        add(layout,text("✦ NEXUS SIDEKICK",29,Color.WHITE));add(layout,text("VOICE AGENT  /  SKILLS  /  GEMINI",12,CYAN));
        LinearLayout hero=card();orb=new VoiceOrbView(this);hero.addView(orb,new LinearLayout.LayoutParams(-1,dp(145)));
        orb.setOnClickListener(v->listenHere());orb.setOnLongClickListener(v->{listenHere();return true;});
        state=text("Ready • floating orb works without leaving the current app",13,Color.WHITE);add(hero,state);
        LinearLayout chat=card();add(chat,text("ASK OR TELL NEXUS",14,CYAN));
        prompt=new EditText(this);prompt.setTextColor(Color.WHITE);prompt.setHintTextColor(0xff8ba6b6);prompt.setHint("E.g. open YouTube, go back, scroll down, or ask a question…");
        prompt.setMinLines(2);add(chat,prompt);
        button(chat,"▶ RUN / ASK GEMINI",()->submit(prompt.getText().toString()));
        button(chat,"🎙 SPEAK",this::listenHere);
        response=text("",15,Color.WHITE);response.setTextIsSelectable(true);add(chat,response);
        LinearLayout credentials=card();add(credentials,text("GEMINI CONNECTION",14,CYAN));
        keyStatus=text("",13,Color.WHITE);add(credentials,keyStatus);
        keyInput=new EditText(this);keyInput.setSingleLine(true);keyInput.setTextColor(Color.WHITE);keyInput.setHintTextColor(0xff8ba6b6);
        keyInput.setHint("Paste Gemini API key (not shown after save)");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);add(credentials,keyInput);
        button(credentials,"SAVE ENCRYPTED KEY",this::saveKey);
        button(credentials,"TEST GEMINI CONNECTION",()->{setState("Testing Google API…");engine.testGemini((message,done)->setState(message));});
        refreshKey();
        LinearLayout skills=card();add(skills,text("PHONE CONTROL & VOICE",14,CYAN));
        button(skills,"ENABLE FLOATING VOICE ASSISTANT",this::startOverlay);
        button(skills,"ENABLE ACCESSIBILITY SKILLS",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        accessStatus=text("",13,Color.WHITE);add(skills,accessStatus);
        button(skills,"STOP FLOATING ASSISTANT",()->stopService(new Intent(this,OverlayService.class)));
        Switch speaks=new Switch(this);speaks.setText("Speak AI replies aloud");speaks.setTextColor(Color.WHITE);
        speaks.setChecked(getSharedPreferences("nexus_settings",0).getBoolean("speak",true));
        speaks.setOnCheckedChangeListener((b,checked)->{getSharedPreferences("nexus_settings",0).edit().putBoolean("speak",checked).apply();if(!checked)engine.stopSpeech();});add(skills,speaks);
        button(skills,"STOP SPEAKING",()->engine.stopSpeech());
        add(skills,text("Allowed skills: open apps, Google/YouTube search, home/back/recents, scrolling, tapping ordinary controls, typing in regular fields. Sensitive actions are blocked.",12,0xffbed1dc));
        LinearLayout diagnostics=card();add(diagnostics,text("DIAGNOSTICS  •  LOCAL",14,CYAN));
        add(diagnostics,text("Includes step names, codes and errors, never API keys, voice transcripts or AI messages.",12,0xffbed1dc));
        logText=text("",11,Color.WHITE);logText.setTextIsSelectable(true);logText.setVisibility(View.GONE);add(diagnostics,logText);
        button(diagnostics,"SHOW / HIDE DIAGNOSTICS",()->{showingLog=!showingLog;logText.setVisibility(showingLog?View.VISIBLE:View.GONE);refreshLog();});
        button(diagnostics,"COPY SAFE DIAGNOSTICS",()->{String log=Diagnostics.read(this);((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NEXUS logs",log));setState("Diagnostics copied");});
        button(diagnostics,"CLEAR DIAGNOSTICS",()->{Diagnostics.clear(this);refreshLog();setState("Diagnostics cleared");});
        add(layout,text("Experimental v0.6 • opt-in Accessibility • Gemini tool calls • no always-on recording",12,0xff8ea9ba));
        Diagnostics.record(this,"APP","NEXUS 0.6 launched");
    }
    private void setState(String s){if(state!=null)state.setText(s);}
    private void refreshKey(){keyStatus.setText(SecureKeyStore.hasKey(this)?"✓ Gemini key saved in Android Keystore":"No Gemini key saved yet");}
    private void refreshLog(){if(logText!=null)logText.setText(Diagnostics.read(this));}
    private void migrateOldKey(){if(SecureKeyStore.hasKey(this))return;String legacy=getPreferences(0).getString("gemini_key","");
        if(!legacy.isEmpty())try{SecureKeyStore.save(this,legacy);getPreferences(0).edit().remove("gemini_key").apply();}catch(Exception ignored){}}
    private void saveKey(){String k=keyInput.getText().toString().trim();if(k.isEmpty()){setState("Paste a key to save it");return;}
        try{SecureKeyStore.save(this,k);getPreferences(0).edit().remove("gemini_key").apply();keyInput.setText("");refreshKey();setState("Gemini key saved securely");}
        catch(Exception e){setState("Could not save key: "+e.getClass().getSimpleName());}}
    private void submit(String text){response.setText("Working…");engine.submit(text,(value,done)->{
        response.setText(value);setState(done?"Ready":"Working…");});}
    private void startOverlay(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},51);return;}
        if(!Settings.canDrawOverlays(this)){setState("Allow Display over other apps, then return and tap Enable again");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        try{startForegroundService(new Intent(this,OverlayService.class));setState("Floating voice assistant enabled • long-press orb to speak");}
        catch(Exception e){setState("Couldn't start overlay: "+e.getClass().getSimpleName());Diagnostics.record(this,"OVERLAY","Start failed "+e.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){super.onRequestPermissionsResult(request,perms,grants);
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(request==51)startOverlay();else if(request==52)listenHere();}
        else setState("Microphone permission denied; enable it to use voice.");}
    private void listenHere(){
        if(listening)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},52);return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){setState("Android Speech Services unavailable");return;}
        if(recognizer!=null)recognizer.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){setState("Listening…");}
            public void onBeginningOfSpeech(){setState("Hearing you…");}
            public void onRmsChanged(float r){orb.setLevel(r);}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){setState("Processing speech…");}
            public void onError(int error){listening=false;orb.setListening(false);setState("Speech error "+error+" • try floating orb");Diagnostics.record(MainActivity.this,"VOICE","In-app error "+error);}
            public void onResults(Bundle b){listening=false;orb.setListening(false);ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(words!=null&&!words.isEmpty()){prompt.setText(words.get(0));submit(words.get(0));}}
            public void onPartialResults(Bundle b){}
            public void onEvent(int t,Bundle b){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try{listening=true;orb.setListening(true);recognizer.startListening(i);}catch(Exception e){listening=false;orb.setListening(false);setState("Speech couldn't start");}
    }
    @Override protected void onResume(){super.onResume();if(accessStatus!=null)accessStatus.setText(NexusAccessibilityService.isEnabled()?"✓ Accessibility skills active":"Accessibility skills off • tap Enable to grant access");refreshLog();}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();super.onDestroy();}
}
