package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Distraction-minimized player; playback remains owned by PlaybackService. */
public class NowPlayingActivity extends Activity {
    private static final int PURPLE=0xFF7F64B7;
    private PlaybackService playback;
    private TextView play,elapsed,remaining,microphone;
    private SeekBar seek;
    private boolean dragging;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){refresh();handler.postDelayed(this,500);}};
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private String format(int ms){
        int s=Math.max(0,ms/1000);
        return String.format(java.util.Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60);
    }
    private String coverFor(String uri){
        SharedPreferences p=getSharedPreferences("library",MODE_PRIVATE);
        try{
            JSONArray books=new JSONArray(p.getString("books","[]"));
            for(int i=0;i<books.length();i++){
                JSONObject b=books.getJSONObject(i);
                if(uri.equals(b.optString("uri")))return b.optString("coverUri","");
            }
        }catch(Exception ignored){}
        return "";
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setStatusBarColor(0xFF392E51);
        getWindow().setNavigationBarColor(0xFF392E51);
        playback=PlaybackService.instance;
        if(playback==null||playback.player==null){
            startActivity(new Intent(this,MainActivity.class).putExtra("section","library"));
            finish();return;
        }
        FrameLayout screen=new FrameLayout(this);screen.setBackgroundColor(PURPLE);
        String cover=coverFor(playback.uri);
        if(!cover.isEmpty()){
            try{
                ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageURI(Uri.parse(cover));
                screen.addView(art,new FrameLayout.LayoutParams(-1,-1));
                View veil=new View(this);veil.setBackgroundColor(0xA82E214A);
                screen.addView(veil,new FrameLayout.LayoutParams(-1,-1));
            }catch(Exception ignored){}
        }
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20),dp(12),dp(20),dp(24));
        screen.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=label("☰",31,true);menu.setContentDescription("Open navigation menu");
        top.addView(menu,new LinearLayout.LayoutParams(dp(64),dp(64)));
        menu.setOnClickListener(v->{
            final String[] items={"Media Library","Notes","Options"};
            new android.app.AlertDialog.Builder(this).setTitle("Audiobook Researcher")
                .setItems(items,(dialog,which)->{
                    String section=which==0?"library":which==1?"notes":"options";
                    startActivity(new Intent(this,MainActivity.class).putExtra("section",section));
                    finish();
                }).show();
        });
        View filler=new View(this);top.addView(filler,new LinearLayout.LayoutParams(0,dp(1),1));
        microphone=label("♩",29,true);microphone.setText("🎙");
        microphone.setContentDescription("Toggle voice activation");
        top.addView(microphone,new LinearLayout.LayoutParams(dp(64),dp(64)));
        microphone.setOnClickListener(v->{
            if(HandsFreeService.serviceActive)stopService(new Intent(this,HandsFreeService.class));
            else if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){
                try{startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));}
                catch(Exception e){android.widget.Toast.makeText(this,"Microphone unavailable: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
            }else requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},300);
            handler.postDelayed(this::refresh,350);
        });
        column.addView(top);
        TextView title=label(playback.title,26,true);
        column.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView chapter=label("NOW PLAYING",14,false);
        chapter.setTextColor(0xFFDCD2F6);column.addView(chapter);
        FrameLayout middle=new FrameLayout(this);
        column.addView(middle,new LinearLayout.LayoutParams(-1,0,1));
        play=label(playback.isPlaying()?"❚❚":"▶",96,true);
        play.setContentDescription("Play or pause audiobook");
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(dp(200),dp(200),Gravity.CENTER);
        play.setBackground(bg(0x557F64B7,100));
        middle.addView(play,center);
        play.setOnClickListener(v->{PlaybackService p=PlaybackService.instance;if(p!=null)p.toggle();refresh();});
        LinearLayout times=new LinearLayout(this);times.setOrientation(LinearLayout.HORIZONTAL);
        elapsed=label("0:00",17,true);elapsed.setGravity(Gravity.LEFT);
        remaining=label("0:00",17,true);remaining.setGravity(Gravity.RIGHT);
        times.addView(elapsed,new LinearLayout.LayoutParams(0,dp(30),1));
        times.addView(remaining,new LinearLayout.LayoutParams(0,dp(30),1));
        column.addView(times);
        seek=new SeekBar(this);seek.setMax(Math.max(1,playback.duration()));
        seek.setProgress(playback.position());seek.setMinimumHeight(dp(50));
        column.addView(seek,new LinearLayout.LayoutParams(-1,dp(54)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){dragging=true;}
            public void onProgressChanged(SeekBar s,int value,boolean user){if(user)elapsed.setText(format(value));}
            public void onStopTrackingTouch(SeekBar s){PlaybackService p=PlaybackService.instance;if(p!=null)p.seek(s.getProgress());dragging=false;}
        });
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        TextView back=label("↶ 15s",33,true),forward=label("30s ↷",33,true);
        for(TextView control:new TextView[]{back,forward}){
            control.setBackground(bg(0x556C529F,22));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(100),1);
            params.setMargins(dp(4),dp(8),dp(4),0);row.addView(control,params);
        }
        back.setContentDescription("Rewind fifteen seconds");
        forward.setContentDescription("Skip forward thirty seconds");
        back.setOnClickListener(v->{PlaybackService p=PlaybackService.instance;if(p!=null)p.seek(p.position()-15000);refresh();});
        forward.setOnClickListener(v->{PlaybackService p=PlaybackService.instance;if(p!=null)p.seek(p.position()+30000);refresh();});
        column.addView(row);
        setContentView(screen);
    }
    private void refresh(){
        PlaybackService p=PlaybackService.instance;
        if(p==null||p.player==null){finish();return;}
        if(play!=null)play.setText(p.isPlaying()?"❚❚":"▶");
        if(microphone!=null){microphone.setAlpha(HandsFreeService.serviceActive?1f:.55f);microphone.setContentDescription(HandsFreeService.serviceActive?"Voice activation on. Tap to stop.":"Voice activation off. Tap to start.");}
        if(elapsed!=null)elapsed.setText(format(p.position()));
        if(remaining!=null)remaining.setText(format(p.duration()));
        if(seek!=null&&!dragging){seek.setMax(Math.max(1,p.duration()));seek.setProgress(p.position());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==300&&results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){
            try{startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));}
            catch(Exception e){android.widget.Toast.makeText(this,"Microphone unavailable: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
        }
    }
    @Override protected void onResume(){super.onResume();handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}
}
