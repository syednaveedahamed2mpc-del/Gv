package com.nexus.sidekick;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout panel;
    private EditText input, apiKey;
    private TextView status, answer;
    private final String model = "gemini-2.5-flash";

    private TextView label(String s, int size) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(Color.WHITE);
        v.setPadding(8, 14, 8, 14); panel.addView(v); return v;
    }
    private void button(String title, Runnable action) {
        Button b = new Button(this); b.setText(title); panel.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10,17,31));
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(26,45,26,35); scroll.addView(panel); setContentView(scroll);
        label("✦ NEXUS // SIDEKICK", 26);
        label("Voice • apps • search • Gemini AI", 15);
        status = label("Ready", 14);
        input = new EditText(this);
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY);
        input.setHint("Search YouTube for cats, open settings, or ask Gemini...");
        panel.addView(input);
        button("RUN / ASK GEMINI", () -> runCommand(input.getText().toString()));
        button("TALK", () -> {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            try { startActivityForResult(i,100); }
            catch(Exception e) { status.setText("Speech recognizer unavailable: " + e.getMessage()); }
        });
        label("Gemini API key (saved only in this app on your phone)", 13);
        apiKey = new EditText(this);
        apiKey.setSingleLine(true); apiKey.setTextColor(Color.WHITE);
        apiKey.setHintTextColor(Color.LTGRAY); apiKey.setHint("Paste Gemini API key");
        apiKey.setInputType(129);
        SharedPreferences prefs = getSharedPreferences("nexus_private", MODE_PRIVATE);
        apiKey.setText(prefs.getString("gemini_key",""));
        panel.addView(apiKey);
        button("SAVE GEMINI KEY", () -> {
            prefs.edit().putString("gemini_key", apiKey.getText().toString().trim()).apply();
            status.setText("Gemini key saved locally. Do not share it or commit it to GitHub.");
        });
        answer = label("", 16);
        button("ENABLE FLOATING SHORTCUT", () -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                try { startForegroundService(new Intent(this,OverlayService.class)); }
                catch(Exception e) { status.setText("Overlay error: " + e.getMessage()); }
            }
        });
        button("STOP FLOATING SHORTCUT", () -> stopService(new Intent(this,OverlayService.class)));
        label("Searches open in your browser or supported app. Gemini prompts are sent to Google's API. This alpha does not execute Gemini-generated actions automatically.", 12);
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req,result,data);
        if (req==100 && result==RESULT_OK && data!=null) {
            ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(words!=null && !words.isEmpty()) { input.setText(words.get(0)); runCommand(words.get(0)); }
        }
    }
    private void browse(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(i);
    }
    private void runCommand(String raw) {
        String q=raw.trim(), l=q.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(q.isEmpty()) {status.setText("Enter a command or question.");return;}
        try {
            String term=null; boolean youtube=false;
            for(String prefix:new String[]{"search youtube for ","search on youtube for ","youtube search ","search youtube ","search for "}) {
                if(l.startsWith(prefix)) {
                    term=q.substring(prefix.length()).trim();
                    youtube=!prefix.equals("search for ");
                    break;
                }
            }
            if(term==null) {
                for(String prefix:new String[]{"search google for ","google search ","search on google for ","search google "}) {
                    if(l.startsWith(prefix)) {term=q.substring(prefix.length()).trim();break;}
                }
            }
            if(term!=null) {
                if(term.isEmpty()) {status.setText("Tell me what to search for.");return;}
                browse(youtube ? "https://www.youtube.com/results?search_query="+Uri.encode(term)
                    : "https://www.google.com/search?q="+Uri.encode(term));
                status.setText("Searching for: "+term);return;
            }
            if(l.equals("open youtube")||l.equals("youtube")) {
                browse("https://www.youtube.com");status.setText("Opening YouTube");return;
            }
            if(l.equals("open settings")||l.equals("settings")) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));status.setText("Opening settings");return;
            }
            if(l.equals("open camera")||l.equals("camera")) {
                startActivity(new Intent("android.media.action.STILL_IMAGE_CAMERA"));return;
            }
            if(l.equals("open browser")||l.equals("open chrome")) {
                browse("https://www.google.com");return;
            }
            if(l.startsWith("open ")) {
                String name=q.substring(5).trim();
                Intent launch=null;
                for(android.content.pm.ApplicationInfo app:getPackageManager().getInstalledApplications(0)) {
                    if(getPackageManager().getApplicationLabel(app).toString().equalsIgnoreCase(name)) {
                        launch=getPackageManager().getLaunchIntentForPackage(app.packageName);break;
                    }
                }
                if(launch!=null) {startActivity(launch);status.setText("Opening "+name);return;}
                status.setText("Can't find "+name+". Try a supported app or ask Gemini.");return;
            }
            askGemini(q);
        } catch(Exception e) {status.setText("Command failed: "+e.getMessage());}
    }
    private void askGemini(String question) {
        String key=getSharedPreferences("nexus_private",MODE_PRIVATE).getString("gemini_key","");
        if(key.isEmpty()) {status.setText("Save your Gemini API key first.");return;}
        status.setText("Asking Gemini...");answer.setText("");
        new Thread(() -> {
            HttpURLConnection connection=null;
            try {
                URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type","application/json");
                connection.setRequestProperty("x-goog-api-key",key);
                connection.setConnectTimeout(15000);connection.setReadTimeout(35000);
                connection.setDoOutput(true);
                JSONObject request=new JSONObject();
                JSONObject part=new JSONObject().put("text",question);
                JSONObject contents=new JSONObject().put("role","user").put("parts",new JSONArray().put(part));
                request.put("contents",new JSONArray().put(contents));
                try(OutputStream out=connection.getOutputStream()) {
                    out.write(request.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code=connection.getResponseCode();
                InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
                StringBuilder body=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                    String line;while((line=reader.readLine())!=null)body.append(line);
                }
                JSONObject response=new JSONObject(body.toString());
                if(code<200||code>=300)throw new Exception("Gemini HTTP "+code+": "+response.optJSONObject("error").optString("message","Request failed"));
                JSONArray candidates=response.optJSONArray("candidates");
                if(candidates==null||candidates.length()==0)throw new Exception("Gemini returned no answer.");
                JSONArray parts=candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                StringBuilder reply=new StringBuilder();
                for(int i=0;i<parts.length();i++) {
                    String t=parts.getJSONObject(i).optString("text","");
                    if(!t.isEmpty())reply.append(t).append("\n");
                }
                String result=reply.toString().trim();
                runOnUiThread(() -> {answer.setText(result.isEmpty()?"No text in response.":result);status.setText("Gemini answered");});
            }catch(Exception ex) {
                String message=ex.getMessage();
                runOnUiThread(() -> status.setText("Gemini error: "+message));
            }finally{if(connection!=null)connection.disconnect();}
        }).start();
    }
}
