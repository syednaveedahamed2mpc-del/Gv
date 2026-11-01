package com.nexus.sidekick;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Gemini REST transport. Discovers an actually available Flash model for the saved key. */
public final class GeminiBridge {
    public static final class Problem extends Exception {
        public final int code;
        Problem(int code,String message){super(message);this.code=code;}
    }
    private static String selected="";
    private GeminiBridge(){}
    public static synchronized void invalidate(){selected="";}
    public static synchronized String discover(String key)throws Exception {
        if(!selected.isEmpty())return selected;
        JSONObject response=request(key,"https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",null);
        JSONArray models=response.optJSONArray("models");
        if(models==null)throw new Problem(0,"Google did not return an accessible model list.");
        ArrayList<String> choices=new ArrayList<>();
        for(int i=0;i<models.length();i++){
            JSONObject item=models.optJSONObject(i);if(item==null)continue;
            String name=item.optString("name","").replaceFirst("^models/","");
            JSONArray methods=item.optJSONArray("supportedGenerationMethods");
            boolean generate=false;
            if(methods!=null)for(int k=0;k<methods.length();k++)if("generateContent".equals(methods.optString(k)))generate=true;
            if(generate&&name.contains("flash")&&!name.contains("image")&&!name.contains("tts"))choices.add(name);
        }
        if(choices.isEmpty())throw new Problem(0,"No Gemini Flash model supports generateContent for this API key.");
        choices.sort((a,b)->Integer.compare(rank(b),rank(a)));
        selected=choices.get(0);return selected;
    }
    private static int rank(String name){String s=name.toLowerCase(Locale.ROOT);
        int score=s.contains("3.5-flash-lite")?7000:s.contains("3.1-flash-lite")?6800:s.contains("3.6-flash")?6600:s.contains("3.8-flash")?6500:s.contains("3.")?6000:s.contains("2.5")?5000:1000;
        if(s.contains("preview")||s.contains("experimental"))score-=80;return score;
    }
    public static JSONObject generate(String key,String model,JSONArray history)throws Exception {
        JSONObject req=new JSONObject();req.put("contents",history);
        req.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",
                "You are NEXUS, a friendly and concise Android voice assistant. Use tools when the user wants an action. " +
                "Never claim to have completed an action without a successful tool result. Call ONE tool at a time. " +
                "Never assist with transactions, sending messages, deleting records, changing accounts or entering credentials autonomously. " +
                "Keep spoken responses short. If you lack Android permission, say so. Never invent UI element names."))));
        req.put("tools",new JSONArray().put(new JSONObject().put("functionDeclarations",declarations())));
        JSONObject envelope;
        try{envelope=request(key,"https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent",req);}
        catch(Problem e){if(e.code==404)invalidate();throw e;}
        JSONArray candidates=envelope.optJSONArray("candidates");
        if(candidates==null||candidates.length()==0)throw new Problem(0,"Gemini returned no candidates (possible content filtering).");
        JSONObject choice=candidates.optJSONObject(0),content=choice==null?null:choice.optJSONObject("content");
        if(content==null||content.optJSONArray("parts")==null)throw new Problem(0,"Gemini returned no usable content.");
        return content;
    }
    private static JSONObject declaration(String name,String description,String property,String explanation)throws Exception{
        JSONObject schema=new JSONObject().put("type","OBJECT");JSONObject props=new JSONObject();
        if(property!=null){props.put(property,new JSONObject().put("type","STRING").put("description",explanation));schema.put("required",new JSONArray().put(property));}
        schema.put("properties",props);
        return new JSONObject().put("name",name).put("description",description).put("parameters",schema);
    }
    private static JSONArray declarations()throws Exception{
        return new JSONArray()
        .put(declaration("open_app","Launch an installed app by its displayed name.","app","App name"))
        .put(declaration("youtube_search","Open YouTube search results.","query","Search text"))
        .put(declaration("web_search","Open a web search in the browser.","query","Search text"))
        .put(declaration("go_home","Navigate to Android Home; requires enabled accessibility service.",null,null))
        .put(declaration("go_back","Press Android Back; requires enabled accessibility service.",null,null))
        .put(declaration("scroll_down","Scroll current visible window down.",null,null))
        .put(declaration("scroll_up","Scroll current visible window up.",null,null))
        .put(declaration("tap_text","Tap visible non-sensitive UI text, after explicit user approval.","text","Exact short visible label"))
        .put(declaration("type_text","Type into focused non-password editable field after user approval. Do not send.","text","Text to enter"));
    }
    private static JSONObject request(String key,String address,JSONObject body)throws Exception{
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(45000);
            c.setRequestProperty("x-goog-api-key",key);c.setRequestProperty("Accept","application/json");
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();InputStream raw=code<400?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            if(raw!=null)try(InputStream in=raw){while((n=in.read(buf))!=-1){if(out.size()>650000)throw new Problem(0,"Gemini response too large.");out.write(buf,0,n);}}
            JSONObject json;
            try{json=new JSONObject(out.toString("UTF-8"));}catch(Exception e){throw new Problem(code,"Gemini sent an invalid response (HTTP "+code+").");}
            if(code>=400){JSONObject e=json.optJSONObject("error");String message=e==null?"API request rejected":e.optString("message","API request rejected");
                message=message.replaceAll("AIza[A-Za-z0-9_-]+","[secret]").replaceAll("[\\r\\n]"," ");
                if(message.length()>220)message=message.substring(0,220);
                throw new Problem(code,"Gemini HTTP "+code+": "+message);}
            return json;
        }catch(SocketTimeoutException e){throw new Problem(0,"Gemini timed out. Check internet connectivity.");}
        finally{if(c!=null)c.disconnect();}
    }
}
