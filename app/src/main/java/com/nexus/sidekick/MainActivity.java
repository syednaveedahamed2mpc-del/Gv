package com.nexus.sidekick;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local intents first. Questions go to Gemini only when the user has saved a key. */
public class MainActivity extends Activity {
    private static final int AUDIO_REQUEST=12;
    private static final int BG=0xff081526, PANEL=0xff11243a, CYAN=0xff20e6ee, MUTED=0xffacc2d1;
    private LinearLayout root;
    private EditText input,keyInput;
    private TextView status,reply,debugText,keyStatus,voiceTitle;
    private VoiceOrbView voiceOrb;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean listening=false,aiBusy=false,speakAnswers=true;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean diagnosticsVisible=false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        tts=new TextToSpeech(this, code -> {if(code!=TextToSpeech.SUCCESS)log("TTS","Voice output unavailable");});
        migrateOldKey();
        makeScreen();
        log("APP","Started • v0.5 • Android "+Build.VERSION.SDK_INT);
        processIntent(getIntent());
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int fill,int radius) {
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String value,int sp,int color) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);return v;
    }
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private void add(LinearLayout to,View v,int height){to.addView(v,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    private LinearLayout block(int paddingTop){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,17,paddingTop,17,16);
        box.setBackground(background(PANEL,22));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));root.addView(box,lp);return box;
    }
    private Button button(String name,Runnable action) {
        Button b=new Button(this);b.setText(name);b.setTextAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff19546a));
        b.setOnClickListener(v->action.run());return b;
    }
    private void makeScreen(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);pad(root,18,24,18,28);
        sc.addView(root);setContentView(sc);
        TextView h=text("✦  NEXUS",30,Color.WHITE);h.setTypeface(null,Typeface.BOLD);add(root,h,-2);
        TextView sub=text("SIDEKICK   /   VOICE + SEARCH + AI",12,CYAN);pad(sub,0,2,0,16);add(root,sub,-2);
        LinearLayout hero=block(16);
        voiceOrb=new VoiceOrbView(this);
        add(hero,voiceOrb,160);
        voiceOrb.setOnClickListener(v->startListening());
        voiceOrb.setOnLongClickListener(v->{startListening();return true;});
        voiceTitle=text("TAP OR HOLD TO TALK",14,CYAN);voiceTitle.setGravity(Gravity.CENTER);add(hero,voiceTitle,-2);
        status=text("Ready • permissions only when needed",13,MUTED);pad(status,0,12,0,0);add(hero,status,-2);
        LinearLayout commands=block(16);
        TextView title=text("WHAT SHOULD I DO?",12,CYAN);add(commands,title,-2);
        input=new EditText(this);input.setHint("Open YouTube and search MrBeast…");input.setHintTextColor(0xff839aab);input.setTextColor(Color.WHITE);input.setTextSize(16);
        input.setMinLines(2);input.setMaxLines(4);input.setBackground(background(0xff0b1b2d,14));pad(input,13,12,13,12);
        LinearLayout.LayoutParams inp=new LinearLayout.LayoutParams(-1,-2);inp.setMargins(0,dp(12),0,dp(10));commands.addView(input,inp);
        add(commands,button("▶  RUN COMMAND",()->runCommand(input.getText().toString())),50);
        add(commands,button("✦  ASK GEMINI",()->askGemini(input.getText().toString())),50);
        reply=text("",15,Color.WHITE);reply.setTextIsSelectable(true);pad(reply,0,9,0,0);add(commands,reply,-2);
        LinearLayout credentials=block(16);
        add(credentials,text("GEMINI 3.8 FLASH",13,CYAN),-2);
        keyStatus=text("",12,MUTED);pad(keyStatus,0,6,0,6);add(credentials,keyStatus,-2);
        keyInput=new EditText(this);keyInput.setTextColor(Color.WHITE);keyInput.setHintTextColor(0xff839aab);
        keyInput.setHint("Enter API key here (not shown after saving)");keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setBackground(background(0xff0b1b2d,12));pad(keyInput,12,12,12,12);add(credentials,keyInput,55);
        add(credentials,button("SAVE ENCRYPTED KEY",this::saveKey),48);
        add(credentials,button("REMOVE SAVED KEY",()->{try{SecureKeyStore.save(this,"");keyInput.setText("");refreshKeyStatus();log("GEMINI","Key removed");}catch(Exception e){setStatus("Unable to remove saved key");}}),44);
        refreshKeyStatus();
        LinearLayout options=block(15);
        add(options,text("SIDEKICK CONTROLS",13,CYAN),-2);
        add(options,button("ENABLE FLOATING ORB",this::startOverlay),50);
        add(options,button("STOP FLOATING ORB",()->{stopService(new Intent(this,OverlayService.class));log("OVERLAY","Stop requested");setStatus("Floating orb stopped");}),48);
        Switch voiceSwitch=new Switch(this);voiceSwitch.setText("Read Gemini answers aloud");voiceSwitch.setTextColor(Color.WHITE);
        voiceSwitch.setChecked(getPreferences(0).getBoolean("speak_answers",true));speakAnswers=voiceSwitch.isChecked();
        voiceSwitch.setOnCheckedChangeListener((v,checked)->{speakAnswers=checked;getPreferences(0).edit().putBoolean("speak_answers",checked).apply();if(!checked&&tts!=null)tts.stop();});add(options,voiceSwitch,56);
        add(options,button("STOP SPEAKING",()->{if(tts!=null)tts.stop();}),46);
        LinearLayout debug=block(14);
        add(debug,text("DIAGNOSTICS  •  PRIVATE ON DEVICE",13,CYAN),-2);
        add(debug,text("Events include result codes and action stages, never API keys or conversation content.",12,MUTED),-2);
        Button show=button("SHOW ACTIVITY LOG",()->{diagnosticsVisible=!diagnosticsVisible;debugText.setVisibility(diagnosticsVisible?View.VISIBLE:View.GONE);refreshLog();});add(debug,show,48);
        debugText=text("",11,MUTED);debugText.setTextIsSelectable(true);debugText.setVisibility(View.GONE);
        pad(debugText,8,10,8,10);debugText.setBackground(background(0xff081525,10));add(debug,debugText,-2);
        add(debug,button("COPY SAFE LOG",this::copyLog),46);
        add(debug,button("SHARE SAFE LOG",this::shareLog),46);
        add(debug,button("CLEAR LOG",()->{Diagnostics.clear(this);refreshLog();setStatus("Log cleared");}),46);
        refreshLog();
    }
    private void setStatus(String message){status.setText(message);}
    private void log(String area,String event){Diagnostics.record(this,area,event);if(debugText!=null)refreshLog();}
    private void refreshLog(){if(debugText!=null)debugText.setText(Diagnostics.read(this));}
    private void copyLog(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NEXUS diagnostics",Diagnostics.read(this)));setStatus("Safe diagnostics copied");}
    private void shareLog(){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"NEXUS Sidekick diagnostic events\n"+Diagnostics.read(this));startActivity(Intent.createChooser(i,"Share NEXUS diagnostics"));}
    private void migrateOldKey() {
        if(SecureKeyStore.hasKey(this))return;
        String legacy=getPreferences(0).getString("gemini_key","");
        if(legacy.isEmpty())return;
        try {SecureKeyStore.save(this,legacy);getPreferences(0).edit().remove("gemini_key").apply();log("SECURITY","Migrated legacy key to Android Keystore");}
        catch(Exception e){log("SECURITY","Key migration failed: "+e.getClass().getSimpleName());}
    }
    private void refreshKeyStatus(){keyStatus.setText(SecureKeyStore.hasKey(this)?"✓ Key saved in Android Keystore":"No Gemini key saved");}
    private void saveKey(){String entered=keyInput.getText().toString().trim();if(entered.isEmpty()){setStatus("Paste your API key to save it");return;}
        try{SecureKeyStore.save(this,entered);getPreferences(0).edit().remove("gemini_key").apply();keyInput.setText("");refreshKeyStatus();log("GEMINI","API key encrypted and saved");setStatus("Key saved securely");}
        catch(Exception e){log("SECURITY","Key save failed: "+e.getClass().getSimpleName());setStatus("Couldn't encrypt key on this device");}}
    private void startOverlay(){if(!Settings.canDrawOverlays(this)){log("OVERLAY","Overlay permission requested");startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        try{startForegroundService(new Intent(this,OverlayService.class));log("OVERLAY","Service start requested");setStatus("Orb enabled • hold 0.65s to talk");}
        catch(Exception e){log("OVERLAY","Service error: "+e.getClass().getSimpleName());setStatus("Overlay blocked by Android: "+e.getClass().getSimpleName());}}
    @Override protected void onResume(){super.onResume();if(debugText!=null)refreshLog();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);processIntent(i);}
    private void processIntent(Intent i){if(i!=null&&i.getBooleanExtra("start_listening",false)){
        i.removeExtra("start_listening");log("VOICE","Long-press request arrived from orb");handler.postDelayed(this::startListening,350);
    }}
    private void startListening(){
        if(listening){log("VOICE","Stop requested");if(recognizer!=null)recognizer.stopListening();setStatus("Finishing speech…");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            log("VOICE","Microphone permission requested");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQUEST);return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            log("VOICE","No visible Android RecognitionService");setStatus("Speech service unavailable. Check Android speech services.");return;
        }
        try{
            if(recognizer!=null)recognizer.destroy();
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle b){log("VOICE","Microphone ready");setStatus("Listening • speak now");}
                @Override public void onBeginningOfSpeech(){log("VOICE","Speech detected");setStatus("Hearing you…");}
                @Override public void onRmsChanged(float rms){voiceOrb.setLevel(rms);}
                @Override public void onBufferReceived(byte[] b){}
                @Override public void onEndOfSpeech(){setStatus("Understanding speech…");log("VOICE","Speech ended");}
                @Override public void onError(int error){listening=false;voiceOrb.setListening(false);log("VOICE","Speech error code "+error);setStatus(speechError(error));}
                @Override public void onResults(Bundle b){listening=false;voiceOrb.setListening(false);
                    ArrayList<String> choices=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(choices==null||choices.isEmpty()){log("VOICE","Empty recognition result");setStatus("Didn't catch that. Try again.");return;}
                    log("VOICE","Recognition completed");input.setText(choices.get(0));runCommand(choices.get(0));}
                @Override public void onPartialResults(Bundle b){}
                @Override public void onEvent(int event,Bundle extras){}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            listening=true;voiceOrb.setListening(true);setStatus("Starting microphone…");log("VOICE","SpeechRecognizer start requested");
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            recognizer.startListening(intent);
        }catch(Exception e){listening=false;voiceOrb.setListening(false);log("VOICE","Start failed: "+e.getClass().getSimpleName());setStatus("Speech couldn't start: "+e.getClass().getSimpleName());}
    }
    private String speechError(int e){switch(e){case 1:return "Voice network timeout";case 2:return "Speech network unavailable";case 3:return "Microphone audio error";case 4:return "Speech server unavailable";case 5:return "Speech client error";case 6:return "No speech heard";case 7:return "I couldn't understand; try again";case 8:return "Speech service busy";case 9:return "Microphone permission denied";case 10:return "Too many speech requests";case 11:return "Speech service disconnected";case 12:return "Language unsupported";case 13:return "Language temporarily unavailable";default:return "Speech error code "+e;}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants){super.onRequestPermissionsResult(r,p,grants);if(r==AUDIO_REQUEST){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){log("VOICE","Microphone permission granted");startListening();}else{log("VOICE","Microphone permission denied");setStatus("Allow microphone permission to talk");}}}
    private void launchUri(String uri,boolean youtube){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(uri));intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if(youtube){Intent direct=new Intent(intent);direct.setPackage("com.google.android.youtube");try{startActivity(direct);return;}catch(Exception ignored){}}
        startActivity(intent);
    }
    private void runCommand(String command){
        CommandRouter.Command parsed=CommandRouter.parse(command);
        log("ROUTER","Action: "+parsed.type.name());
        if(parsed.type==CommandRouter.Type.EMPTY){setStatus("Type or say a command");return;}
        try{
            switch(parsed.type){
                case YOUTUBE_SEARCH:
                    if(parsed.value.isEmpty()){setStatus("What should I search for?");return;}
                    launchUri("https://www.youtube.com/results?search_query="+Uri.encode(parsed.value),true);
                    setStatus("YouTube search opened");log("SEARCH","YouTube intent dispatched");break;
                case GOOGLE_SEARCH:
                    if(parsed.value.isEmpty()){setStatus("What should I search for?");return;}
                    launchUri("https://www.google.com/search?q="+Uri.encode(parsed.value),false);
                    setStatus("Google search opened");log("SEARCH","Google intent dispatched");break;
                case OPEN_SETTINGS:startActivity(new Intent(Settings.ACTION_SETTINGS));setStatus("Opened settings");break;
                case OPEN_CAMERA:startActivity(new Intent("android.media.action.STILL_IMAGE_CAMERA"));setStatus("Opened camera");break;
                case OPEN_APP:launchApp(parsed.value);break;
                case CHAT:askGemini(parsed.value);break;
                default:break;
            }
        }catch(Exception e){log("ROUTER","Dispatch failed: "+e.getClass().getSimpleName());setStatus("Could not open destination: "+e.getClass().getSimpleName());}
    }
    private void launchApp(String label){
        String normalized=label.toLowerCase(Locale.ROOT);
        if(normalized.equals("youtube")||normalized.equals("you tube")){launchUri("https://www.youtube.com",true);log("APP","YouTube opened");return;}
        if(normalized.equals("browser")||normalized.equals("chrome")){launchUri("https://www.google.com",false);log("APP","Browser opened");return;}
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(launcher,0);
        for(ResolveInfo app:apps){
            if(app.loadLabel(getPackageManager()).toString().equalsIgnoreCase(label)){
                Intent launch=getPackageManager().getLaunchIntentForPackage(app.activityInfo.packageName);
                if(launch!=null){startActivity(launch);log("APP","Launch intent dispatched");setStatus("Opened "+label);return;}
            }
        }
        log("APP","No matching launchable app");setStatus("Can't find app: "+label);
    }
    private void askGemini(String question){
        if(question.trim().isEmpty()){setStatus("Ask a question first");return;}
        if(aiBusy){setStatus("Gemini request already running");return;}
        final String apiKey;
        try{apiKey=SecureKeyStore.load(this);}catch(Exception e){log("SECURITY","Key load failed: "+e.getClass().getSimpleName());setStatus("Unable to unlock saved API key");return;}
        if(apiKey.isEmpty()){setStatus("Save your Gemini API key first");return;}
        aiBusy=true;reply.setText("");setStatus("Gemini is thinking…");log("GEMINI","Request started: preferred gemini-3.8-flash");
        worker.execute(()->{
            try{
                GeminiClient.Result result=GeminiClient.ask(apiKey,question);
                runOnUiThread(()->{
                    aiBusy=false;reply.setText(result.text);setStatus("Gemini answered • "+result.model);
                    log("GEMINI","HTTP "+result.status+" • model "+result.model);
                    if(speakAnswers&&tts!=null)tts.speak(result.text.length()>1000?result.text.substring(0,1000):result.text,TextToSpeech.QUEUE_FLUSH,null,"nexus-reply");
                });
            }catch(GeminiClient.GeminiException e){runOnUiThread(()->{aiBusy=false;log("GEMINI","HTTP "+e.httpCode+" • request rejected");setStatus(GeminiClient.describe(e.httpCode));});}
            catch(Exception e){runOnUiThread(()->{aiBusy=false;log("GEMINI","Network/parse error: "+e.getClass().getSimpleName());setStatus("Gemini connection failed: "+e.getClass().getSimpleName());});}
        });
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();super.onDestroy();}
}
