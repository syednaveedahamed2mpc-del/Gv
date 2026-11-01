package com.nexus.sidekick;
import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.provider.Settings;import android.graphics.Color;import android.view.*;import android.widget.*;
public class MainActivity extends Activity {
 LinearLayout panel; EditText input; TextView status;
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(8,14,8,14);panel.addView(t);return t;}
 void button(String s,Runnable r){Button b=new Button(this);b.setText(s);panel.addView(b);b.setOnClickListener(v->r.run());}
 public void onCreate(Bundle b){super.onCreate(b);ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Color.rgb(10,17,31));panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(26,55,26,35);sc.addView(panel);setContentView(sc);
 text("✦ NEXUS // SIDEKICK",26);text("Android assistant • local commands • voice input • floating shortcut",14);
 status=text("Ready. Commands run on your device.",14);
 input=new EditText(this);input.setSingleLine(false);input.setHint("Try: open YouTube, search YouTube for cats, open settings");input.setTextColor(Color.WHITE);input.setHintTextColor(Color.LTGRAY);panel.addView(input);
 button("RUN COMMAND",()->runCommand(input.getText().toString()));
 button("TALK",()->{Intent i=new Intent("android.speech.action.RECOGNIZE_SPEECH");i.putExtra("android.speech.extra.LANGUAGE_MODEL","free_form");try{startActivityForResult(i,100);}catch(Exception e){status.setText("Voice recognition not available on this phone.");}});
 button("ENABLE FLOATING SHORTCUT",()->{if(!Settings.canDrawOverlays(this)){Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);}else{Intent i=new Intent(this,OverlayService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Floating shortcut started.");}});
 button("STOP FLOATING SHORTCUT",()->{stopService(new Intent(this,OverlayService.class));status.setText("Floating shortcut stopped.");});
 text("Privacy: no API key required; voice recognition uses the installed Android speech service.",13);
 }
 protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==100&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> a=data.getStringArrayListExtra("android.speech.extra.RESULTS");if(a!=null&&!a.isEmpty()){input.setText(a.get(0));runCommand(a.get(0));}}}
 void runCommand(String raw){String q=raw.trim(),l=q.toLowerCase(java.util.Locale.ROOT);try{
 if(l.startsWith("search youtube for ")){String term=q.substring(19).trim();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(term))));}
 else if(l.startsWith("search google for ")){String term=q.substring(18).trim();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(term))));}
 else if(l.contains("youtube")){Intent i=getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");startActivity(i!=null?i:new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com")));}
 else if(l.contains("settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));
 else if(l.contains("camera")){Intent i=new Intent("android.media.action.STILL_IMAGE_CAMERA");startActivity(i);}
 else if(l.contains("browser")||l.contains("chrome"))startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com")));
 else if(l.startsWith("open ")){String name=q.substring(5).trim();Intent launch=null;for(android.content.pm.ApplicationInfo a:getPackageManager().getInstalledApplications(0)){String label=getPackageManager().getApplicationLabel(a).toString();if(label.equalsIgnoreCase(name)){launch=getPackageManager().getLaunchIntentForPackage(a.packageName);break;}}if(launch==null)throw new Exception("App not found or not visible to Android: "+name);startActivity(launch);}
 else {status.setText("Unsupported command. Try: open YouTube, open settings, search YouTube for cats.");return;}
 status.setText("Executed: "+q);
 }catch(Exception e){status.setText("Could not execute: "+e.getMessage());}}
}
