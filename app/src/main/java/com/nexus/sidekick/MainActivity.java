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
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** NEXUS configuration; long-press voice interactions live independently in OverlayService. */
public final class MainActivity extends Activity implements NexusAgent.Listener {
    private static final int MIC_SETUP=31,MIC_TALK=32,BG=0xff071827,PANEL=0xff123144,CYAN=0xff22e6ef;
    private LinearLayout layout,approvalBox;private TextView status,answer,logs,keyStatus;
    private EditText message,keyField;private VoiceOrbView voice;private SpeechRecognizer recognizer;
    private TextToSpeech tts;private boolean speak=true;private final Handler ui=new Handler(Looper.getMainLooper());
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(6));return v;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(bg(PANEL,17));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(13);layout.addView(c,lp);return c;}
    private Button btn(LinearLayout box,String label,Runnable onClick){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1a697c));box.addView(b);b.setOnClickListener(v->onClick.run());return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        migrateOldKey();tts=new TextToSpeech(this,result->{if(result!=TextToSpeech.SUCCESS)Diagnostics.record(this,"TTS","Init unavailable");});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(20),dp(27),dp(20),dp(24));scroll.addView(layout);setContentView(scroll);
        layout.addView(text("✦  NEXUS SIDEKICK",25,Color.WHITE));layout.addView(text("GEMINI • VOICE • ANDROID SKILLS",12,CYAN));
        LinearLayout hero=card();voice=new VoiceOrbView(this);hero.addView(voice,new LinearLayout.LayoutParams(-1,dp(140)));
        voice.setOnClickListener(v->startMic());voice.setOnLongClickListener(v->{startMic();return true;});
        status=text("Ready • hold the floating orb to talk over any app",13,Color.WHITE);hero.addView(status);
        LinearLayout commands=card();commands.addView(text("YOUR REQUEST",13,CYAN));
        message=new EditText(this);message.setTextColor(Color.WHITE);message.setHintTextColor(0xff97b2bf);message.setHint("Open YouTube, search MrBeast or ask Gemini…");message.setMinLines(2);message.setMaxLines(4);message.setBackground(bg(BG,12));message.setPadding(dp(13),dp(12),dp(13),dp(12));commands.addView(message);
        btn(commands,"▶ RUN / SEARCH",()->route(message.getText().toString()));
        btn(commands,"✦ GEMINI AGENT (TOOLS + CHAT)",()->NexusAgent.get(this).ask(message.getText().toString(),this));
        btn(commands,"🎙 TALK",this::startMic);
        answer=text("",15,Color.WHITE);answer.setTextIsSelectable(true);commands.addView(answer);
        approvalBox=new LinearLayout(this);approvalBox.setOrientation(1);approvalBox.setVisibility(View.GONE);commands.addView(approvalBox);
        LinearLayout cloud=card();cloud.addView(text("GEMINI CONNECTION",13,CYAN));keyStatus=text("",13,Color.WHITE);cloud.addView(keyStatus);
        keyField=new EditText(this);keyField.setHint("Paste Gemini API key");keyField.setHintTextColor(0xff97b2bf);keyField.setTextColor(Color.WHITE);keyField.setSingleLine(true);keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);cloud.addView(keyField);
        btn(cloud,"SAVE ENCRYPTED KEY",this::saveKey);btn(cloud,"TEST GEMINI CONNECTION",()->NexusAgent.get(this).checkConnection(this));
        btn(cloud,"REMOVE KEY",()->{try{SecureKeyStore.save(this,"");GeminiBridge.invalidate();keyField.setText("");refreshKey();status("Gemini key removed.");}catch(Exception e){status("Couldn't remove key.");}});refreshKey();
        LinearLayout controls=card();controls.addView(text("FLOATING VOICE & PHONE CONTROL",13,CYAN));
        controls.addView(text("Grant microphone and floating-window permission once. Long-press the orb to listen without opening NEXUS. Accessibility skills require separate explicit permission.",13,Color.WHITE));
        btn(controls,"ENABLE FLOATING VOICE",this::enableOverlay);
        btn(controls,"STOP FLOATING ASSISTANT",()->{stopService(new Intent(this,OverlayService.class));status("Floating assistant stopped.");});
        btn(controls,"ENABLE OPTIONAL ACCESSIBILITY SKILLS",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        controls.addView(text("Accessibility access can read visible UI elements and perform approved taps. Enable only if you trust this build. Never give it passwords or payment information.",12,0xffb7d2dc));
        Switch output=new Switch(this);output.setText("Speak assistant replies aloud");output.setTextColor(Color.WHITE);output.setChecked(getPreferences(0).getBoolean("speak_answers",true));speak=output.isChecked();output.setOnCheckedChangeListener((v,on)->{speak=on;getPreferences(0).edit().putBoolean("speak_answers",on).apply();if(!on&&tts!=null)tts.stop();});controls.addView(output);
        btn(controls,"STOP SPEAKING",()->{if(tts!=null)tts.stop();});btn(controls,"CLEAR CHAT HISTORY",()->{NexusAgent.get(this).clearConversation();answer.setText("");status("Conversation history cleared.");});
        LinearLayout debug=card();debug.addView(text("PRIVATE DIAGNOSTICS",13,CYAN));debug.addView(text("Records timestamps, action names and error codes; no API keys, prompts, recordings or replies.",12,Color.WHITE));
        logs=text("",11,0xffb8cfdb);logs.setTextIsSelectable(true);logs.setVisibility(View.GONE);debug.addView(logs);
        btn(debug,"SHOW / HIDE DIAGNOSTICS",()->{logs.setVisibility(logs.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);logs.setText(Diagnostics.read(this));});
        btn(debug,"COPY SAFE LOG",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NEXUS safe log",Diagnostics.read(this)));status("Log copied.");});
        btn(debug,"CLEAR LOG",()->{Diagnostics.clear(this);logs.setText("");status("Log cleared.");});
        Diagnostics.record(this,"APP","v0.6 launched");
    }
    private void migrateOldKey(){if(SecureKeyStore.hasKey(this))return;String old=getPreferences(0).getString("gemini_key","");if(old.isEmpty())return;try{SecureKeyStore.save(this,old);getPreferences(0).edit().remove("gemini_key").apply();}catch(Exception e){Diagnostics.record(this,"KEY","Old key migration failed");}}
    private void refreshKey(){keyStatus.setText(SecureKeyStore.hasKey(this)?"✓ API key encrypted on device":"No Gemini key saved");}
    private void saveKey(){String s=keyField.getText().toString().trim();if(s.isEmpty()){status("Enter a Gemini API key.");return;}
        try{SecureKeyStore.save(this,s);GeminiBridge.invalidate();keyField.setText("");refreshKey();status("Key saved. Tap TEST GEMINI CONNECTION.");}catch(Exception e){status("Unable to encrypt API key.");}}
    private void enableOverlay(){if(!Settings.canDrawOverlays(this)){
            status("Grant Display over other apps, then return here.");startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_SETUP);return;}
        try{startForegroundService(new Intent(this,OverlayService.class));status("Floating microphone ready. Hold orb to talk.");Diagnostics.record(this,"OVERLAY","Microphone FGS started while activity visible");}
        catch(Exception e){Diagnostics.record(this,"OVERLAY","Start blocked "+e.getClass().getSimpleName());status("Android blocked service: "+e.getClass().getSimpleName());}}
    private void startMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_TALK);return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status("Android speech recognizer unavailable.");return;}
        try{
            if(recognizer!=null)recognizer.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle b){status("Listening…");}
                @Override public void onBeginningOfSpeech(){status("Hearing you…");}
                @Override public void onRmsChanged(float level){voice.setLevel(level);}
                @Override public void onBufferReceived(byte[] data){}
                @Override public void onEndOfSpeech(){status("Understanding…");}
                @Override public void onError(int code){voice.setListening(false);status("Speech error "+code+" • check microphone & Android speech services");Diagnostics.record(MainActivity.this,"VOICE","Error "+code);}
                @Override public void onResults(Bundle b){voice.setListening(false);ArrayList<String> phrases=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(phrases!=null&&!phrases.isEmpty()){message.setText(phrases.get(0));route(phrases.get(0));}}
                @Override public void onPartialResults(Bundle b){}
                @Override public void onEvent(int id,Bundle b){}
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voice.setListening(true);status("Starting microphone…");recognizer.startListening(i);
        }catch(Exception e){voice.setListening(false);status("Mic unavailable: "+e.getClass().getSimpleName());}}
    private void route(String original){CommandRouter.Command cmd=CommandRouter.parse(original);
        if(cmd.type==CommandRouter.Type.EMPTY){status("Enter a request.");return;}
        if(cmd.type==CommandRouter.Type.CHAT||(cmd.type==CommandRouter.Type.OPEN_APP&&(original.toLowerCase(Locale.ROOT).contains(" and ")||original.toLowerCase(Locale.ROOT).contains(" then ")))){NexusAgent.get(this).ask(original,this);return;}
        String tool=null,value=cmd.value;
        switch(cmd.type){case OPEN_APP:tool="open_app";break;case OPEN_SETTINGS:tool="open_app";value="settings";break;case OPEN_CAMERA:tool="open_app";value="camera";break;
            case GOOGLE_SEARCH:tool="web_search";break;case YOUTUBE_SEARCH:tool="youtube_search";break;default:break;}
        if(tool==null)return;
        try{JSONObject args=new JSONObject().put(tool.equals("open_app")?"app":"query",value);reply(SkillExecutor.execute(this,tool,args,true).message);}
        catch(Exception e){status("Command failed: "+e.getClass().getSimpleName());}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] granted){super.onRequestPermissionsResult(request,permissions,granted);
        if(granted.length==0||granted[0]!=PackageManager.PERMISSION_GRANTED){status("Microphone permission denied.");return;}
        if(request==MIC_SETUP)enableOverlay();else if(request==MIC_TALK)startMic();}
    @Override public void status(String s){ui.post(()->{if(status!=null)status.setText(s);if(logs!=null&&logs.getVisibility()==View.VISIBLE)logs.setText(Diagnostics.read(this));});}
    @Override public void reply(String s){ui.post(()->{if(answer!=null)answer.setText(s);status("NEXUS replied");if(tts!=null&&speak)tts.speak(s.length()>700?s.substring(0,700):s,TextToSpeech.QUEUE_FLUSH,null,"nexus_main");});}
    @Override public void approval(String description,Runnable yes,Runnable no){ui.post(()->{approvalBox.setVisibility(View.VISIBLE);approvalBox.removeAllViews();approvalBox.addView(text(description,14,CYAN));
            btn(approvalBox,"✓ APPROVE ONE ACTION",()->{approvalBox.setVisibility(View.GONE);yes.run();});btn(approvalBox,"× DENY",()->{approvalBox.setVisibility(View.GONE);no.run();});});}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
