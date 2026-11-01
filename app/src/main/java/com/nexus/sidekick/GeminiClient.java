package com.nexus.sidekick;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** No Android dependencies; never sends a key in a URL and never logs requests or responses. */
public final class GeminiClient {
    private static final String[] MODELS = {"gemini-3.8-flash", "gemini-3.6-flash"};
    public static final class Result {
        public final String text, model;
        public final int status;
        Result(String text, String model, int status) { this.text=text;this.model=model;this.status=status; }
    }
    public static final class GeminiException extends Exception {
        public final int httpCode;
        GeminiException(int code, String message) { super(message);httpCode=code; }
    }
    private GeminiClient() {}
    public static Result ask(String key, String question) throws Exception {
        GeminiException last=null;
        for (int i=0;i<MODELS.length;i++) {
            try {return request(key,question,MODELS[i]);}
            catch (GeminiException ex) {
                last=ex;
                // Switch models only on missing/unavailable model. NEVER retry quota or auth errors.
                if (ex.httpCode!=404 || i==MODELS.length-1) throw ex;
            }
        }
        throw last;
    }
    private static Result request(String key,String question,String model) throws Exception {
        HttpURLConnection c=null;
        try {
            URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
            c=(HttpURLConnection)url.openConnection();
            c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(45000);
            c.setRequestProperty("Content-Type","application/json");
            c.setRequestProperty("x-goog-api-key",key);
            c.setDoOutput(true);
            JSONObject req=new JSONObject().put("contents",new JSONArray().put(
                new JSONObject().put("role","user").put("parts",new JSONArray().put(new JSONObject().put("text",question)))));
            try(OutputStream out=c.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
            int status=c.getResponseCode();
            InputStream stream=status<300?c.getInputStream():c.getErrorStream();
            if(status>=300)throw new GeminiException(status, describe(status));
            if(stream==null)throw new GeminiException(status,"Empty response");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            try(InputStream in=stream){while((n=in.read(buffer))!=-1){if(bytes.size()>600000)throw new Exception("Response too large");bytes.write(buffer,0,n);}}
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            JSONArray candidates=response.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0)throw new Exception("No reply (possibly filtered)");
            JSONObject content=candidates.getJSONObject(0).optJSONObject("content");
            if(content==null)throw new Exception("No reply content");
            JSONArray parts=content.optJSONArray("parts");
            if(parts==null)throw new Exception("No reply text");
            StringBuilder text=new StringBuilder();
            for(int i=0;i<parts.length();i++)text.append(parts.getJSONObject(i).optString("text",""));
            if(text.length()==0)throw new Exception("Gemini returned an empty reply");
            return new Result(text.toString(),model,status);
        } finally { if(c!=null)c.disconnect(); }
    }
    public static String describe(int http) {
        switch(http){
            case 400:return "Invalid request. Try shorter text.";
            case 401:case 403:return "API key invalid, restricted, or access denied.";
            case 404:return "Model unavailable for this API key.";
            case 429:return "Rate limit or quota reached. Check Gemini API billing/limits.";
            case 500:case 502:case 503:case 504:return "Google server temporarily unavailable.";
            default:return "Gemini request failed (HTTP "+http+").";
        }
    }
}