package com.cosmerearchives.audiobookresearcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Shared persistent organization and non-destructive skip markers. */
final class BookOrganizer {
    static final String PREFS="library", KEY="customBooks";
    static JSONArray groups(Context c){
        try{return new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static void save(Context c,JSONArray groups){c.getSharedPreferences(PREFS,0).edit().putString(KEY,groups.toString()).apply();}
    static JSONObject groupFor(Context c,String uri){
        JSONArray groups=groups(c);
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);
            if(group!=null&&indexOf(group.optJSONArray("parts"),uri)>=0)return group;
        }
        return null;
    }
    static int indexOf(JSONArray arr,String uri){
        if(arr==null)return -1;
        for(int i=0;i<arr.length();i++)if(uri.equals(arr.optString(i)))return i;
        return -1;
    }
    static String next(Context c,String uri){
        JSONObject group=groupFor(c,uri);if(group==null)return null;
        JSONArray parts=group.optJSONArray("parts");int at=indexOf(parts,uri);
        if(parts==null)return null;
        for(int i=at+1;i<parts.length();i++){
            String next=parts.optString(i);
            if(c.getSharedPreferences(PREFS,0).getBoolean("includeWrapping",false)||!isWholeWrapping(c,next))return next;
        }
        return null;
    }
    static boolean isWholeWrapping(Context c,String uri){
        JSONObject markers=markers(c);JSONObject m=markers.optJSONObject(uri);
        return m!=null&&m.optBoolean("whole",false);
    }
    static JSONObject markers(Context c){
        try{return new JSONObject(c.getSharedPreferences(PREFS,0).getString("wrappingMarkers","{}"));}
        catch(Exception e){return new JSONObject();}
    }
    static JSONObject marker(Context c,String uri){
        JSONObject obj=markers(c).optJSONObject(uri);return obj==null?new JSONObject():obj;
    }
    static void setMarker(Context c,String uri,JSONObject marker){
        JSONObject all=markers(c);try{all.put(uri,marker);}catch(Exception ignored){}
        c.getSharedPreferences(PREFS,0).edit().putString("wrappingMarkers",all.toString()).apply();
    }
    static int skipPosition(Context c,String uri,int pos,int duration){
        if(c.getSharedPreferences(PREFS,0).getBoolean("includeWrapping",false))return pos;
        JSONObject m=marker(c,uri);
        JSONArray ranges=m.optJSONArray("ranges");if(ranges==null)return pos;
        for(int step=0;step<ranges.length();step++){
            boolean changed=false;
            for(int i=0;i<ranges.length();i++){
                JSONArray pair=ranges.optJSONArray(i);if(pair==null)continue;
                int start=Math.max(0,pair.optInt(0)),end=Math.min(duration,pair.optInt(1));
                if(pos>=start&&pos<end){pos=end;changed=true;}
            }
            if(!changed)break;
        }
        return pos;
    }
}
