package com.nexus.sidekick;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared assistant brain for floating overlay and app. Work never blocks Android main thread. */
public final class AssistantEngine {
    public interface Listener {void onUpdate(String message,boolean finished);}
    private static AssistantEngine instance;
    private final Context app;
    private final DeviceSkills skills;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final List<JSONObject> history=new ArrayList<>();
    private TextToSpeech tts;
    private volatile boolean ttsReady=false;
    private AssistantEngine(Context c){
        app=c.getApplicationContext();skills=new DeviceSkills(app);
        tts=new TextToSpeech(app,code->{ttsReady=code==TextToSpeech.SUCCESS;});
    }
    public static synchronized AssistantEngine get(Context c){if(instance==null)instance=new AssistantEngine(c);return instance;}
    private void notify(Listener l,String value,boolean done){main.post(()->{if(l!=null)l.onUpdate(value,done);});}
    private void log(String stage,String event){Diagnostics.record(app,stage,event);}
    public void speak(String text){if(ttsReady&&app.getSharedPreferences("nexus_settings",0).getBoolean("speak",true)){
        main.post(()->tts.speak(ToolGuard.truncate(text,650),TextToSpeech.QUEUE_FLUSH,null,"nexus-answer"));}}
    public void stopSpeech(){if(tts!=null)main.post(()->tts.stop());}
    public void submit(String userText,Listener listener){
        String text=userText==null?"":userText.trim();
        if(text.isEmpty()){notify(listener,"Say or type a request.",true);return;}
        if(!busy.compareAndSet(false,true)){notify(listener,"One request is already running.",true);return;}
        notify(listener,"Working on your request…",false);
        work.execute(()->{String answer;try{answer=process(text,listener);}catch(AgentGemini.ApiError e){
                log("GEMINI","HTTP "+e.status);
                answer=e.status==0?"Gemini connection timed out. Check your internet and retry.":
                    e.status==401||e.status==403?"Gemini API key was rejected. Check the key or its restrictions.":
                    e.status==429?"Gemini quota reached. Try later or check your API usage.":
                    "Gemini error HTTP "+e.status+": "+ToolGuard.truncate(e.getMessage(),100);
            }catch(Exception e){log("AGENT","Failure: "+e.getClass().getSimpleName());answer="Something went wrong: "+e.getClass().getSimpleName();}
            busy.set(false);notify(listener,answer,true);speak(answer);
        });
    }
    private String process(String text,Listener listener) throws Exception {
        CommandRouter.Command local=CommandRouter.parse(text);
        // Simple deterministic device intents always work offline.
        if(local.type!=CommandRouter.Type.CHAT&&local.type!=CommandRouter.Type.EMPTY){
            JSONObject args=new JSONObject();String name="";
            switch(local.type){
                case YOUTUBE_SEARCH:name="youtube_search";args.put("query",local.value);break;
                case GOOGLE_SEARCH:name="web_search";args.put("query",local.value);break;
                case OPEN_APP:name="open_app";args.put("app",local.value);break;
                case OPEN_SETTINGS:name="open_settings";break;
                case OPEN_CAMERA:
                    name="open_app";args.put("app","Camera");break;
                default:break;
            }
            if(!name.isEmpty()){
                skills.forRequest(text);String result=skills.run(name,args);
                log("ROUTER","Local "+name);return result;
            }
        }
        String key=SecureKeyStore.load(app);
        if(key.isEmpty())return "Save your Gemini API key once in the main NEXUS app to use AI commands.";
        skills.forRequest(text);
        JSONArray conversation=new JSONArray();
        synchronized(history){for(JSONObject item:history)conversation.put(item);}
        JSONObject user=AgentGemini.content("user",new JSONArray().put(AgentGemini.textPart(text)));
        conversation.put(user);
        log("GEMINI","Request to "+AgentGemini.MODEL);
        String answer="";
        for(int round=0;round<4;round++){
            JSONObject response=AgentGemini.ask(key,conversation,round<3);
            JSONArray parts=response.optJSONArray("parts");
            if(parts==null)throw new Exception("Empty Gemini answer");
            conversation.put(response);
            JSONArray responses=new JSONArray();boolean called=false;
            StringBuilder words=new StringBuilder();
            for(int i=0;i<parts.length();i++){
                JSONObject part=parts.optJSONObject(i);if(part==null)continue;
                String value=part.optString("text","");if(!value.isEmpty())words.append(value);
                JSONObject call=part.optJSONObject("functionCall");if(call==null)continue;
                called=true;String name=call.optString("name","");
                String result=skills.run(name,call.optJSONObject("args"));
                log("SKILL",name+" => "+(result.startsWith("Completed")||result.startsWith("Opened")?"ok":"not completed"));
                notify(listener,"Action: "+name+" • "+ToolGuard.truncate(result,85),false);
                JSONObject responseBody=new JSONObject().put("result",result);
                JSONObject reply=new JSONObject().put("name",name).put("response",responseBody);
                // For APIs returning a call id, maintain the exact identifier in the response.
                if(call.has("id"))reply.put("id",call.getString("id"));
                responses.put(new JSONObject().put("functionResponse",reply));
            }
            if(!called){answer=words.toString().trim();break;}
            conversation.put(AgentGemini.content("user",responses));
            if(round==2){answer="I reached the safe three-step action limit. Tell me what to do next.";break;}
        }
        if(answer.isEmpty())answer="The AI did not return a final answer. Try rephrasing.";
        synchronized(history){history.add(user);history.add(AgentGemini.content("model",new JSONArray().put(AgentGemini.textPart(answer))));
            while(history.size()>10)history.remove(0);
        }
        log("GEMINI","Finished");return answer;
    }
    public void testGemini(Listener listener){
        work.execute(()->{try{String key=SecureKeyStore.load(app);if(key.isEmpty()){notify(listener,"No Gemini API key saved.",true);return;}
                AgentGemini.checkConnection(key);log("GEMINI","Connection test HTTP 200");notify(listener,"Gemini API connection OK • "+AgentGemini.MODEL,true);
            }catch(AgentGemini.ApiError e){log("GEMINI","Connection test HTTP "+e.status);notify(listener,"Gemini connection failed (HTTP "+e.status+")",true);
            }catch(Exception e){log("GEMINI","Connection test "+e.getClass().getSimpleName());notify(listener,"Connection failed: "+e.getClass().getSimpleName(),true);}});
    }
}
