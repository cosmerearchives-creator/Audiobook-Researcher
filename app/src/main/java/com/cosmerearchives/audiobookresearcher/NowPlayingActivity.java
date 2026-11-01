package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.graphics.BitmapFactory;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceCallback;
import android.content.pm.PackageManager;
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
    private String displayedUri="";
    private TextView play,elapsed,remaining,microphone;
    private SeekBar seek;
    private boolean dragging;
    private AudioManager audioManager;
    private boolean bluetoothConnected=false, bluetoothStartedVoice=false;
    private final AudioDeviceCallback bluetoothWatcher=new AudioDeviceCallback(){
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices){handler.post(()->syncBluetooth());}
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices){handler.post(()->syncBluetooth());}
    };
    private boolean bluetoothOutputConnected(){
        if(audioManager==null)return false;
        for(AudioDeviceInfo d:audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
            int t=d.getType();
            if(t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                ||t==AudioDeviceInfo.TYPE_BLE_HEADSET || t==AudioDeviceInfo.TYPE_BLE_SPEAKER)return true;
        }
        return false;
    }
    private void startBluetoothVoice(){
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},300);return;}
        try{
            startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));
            bluetoothStartedVoice=true;
        }catch(Exception e){android.widget.Toast.makeText(this,"Voice activation unavailable: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    private void syncBluetooth(){
        boolean current=bluetoothOutputConnected();
        if(!current){
            if(bluetoothStartedVoice && HandsFreeService.serviceActive)stopService(new Intent(this,HandsFreeService.class));
            bluetoothStartedVoice=false;
            getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("bluetoothManualOff",false).apply();
        }else if(!HandsFreeService.serviceActive &&
            !getSharedPreferences("library",MODE_PRIVATE).getBoolean("bluetoothManualOff",false)){
            startBluetoothVoice();
        }
        bluetoothConnected=current;
        refresh();
    }

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
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        playback=PlaybackService.instance;
        if(playback==null||playback.player==null){
            startActivity(new Intent(this,MainActivity.class).putExtra("section","library"));
            finish();return;
        }
        displayedUri=playback.uri;
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
            final String[] items={"Media Library","Notes","Options","Tools"};
            new android.app.AlertDialog.Builder(this).setTitle("Audiobook Researcher")
                .setItems(items,(dialog,which)->{
                    String section=which==0?"library":which==1?"notes":which==2?"options":"tools";
                    startActivity(new Intent(this,MainActivity.class).putExtra("section",section));
                    finish();
                }).show();
        });
        View filler=new View(this);top.addView(filler,new LinearLayout.LayoutParams(0,dp(1),1));
        microphone=label("♩",29,true);microphone.setText("🎙");
        microphone.setContentDescription("Toggle voice activation");
        top.addView(microphone,new LinearLayout.LayoutParams(dp(64),dp(64)));
        microphone.setOnClickListener(v->{
            if(HandsFreeService.serviceActive){
                if(bluetoothConnected)getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("bluetoothManualOff",true).apply();
                bluetoothStartedVoice=false;
                stopService(new Intent(this,HandsFreeService.class));
            }
            else if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){
                try{
                    getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("bluetoothManualOff",false).apply();
                    startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));
                    bluetoothStartedVoice=false;
                }
                catch(Exception e){android.widget.Toast.makeText(this,"Microphone unavailable: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
            }else requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},300);
            handler.postDelayed(this::refresh,350);
        });
        column.addView(top);
        org.json.JSONObject customGroup=BookOrganizer.groupFor(this,playback.uri);
        String displayTitle=customGroup==null?playback.title:customGroup.optString("name",playback.title);
        TextView title=label(displayTitle,26,true);
        column.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView chapter=label(customGroup==null?"NOW PLAYING":playback.title,14,false);
        chapter.setTextColor(0xFFDCD2F6);column.addView(chapter);
        FrameLayout middle=new FrameLayout(this);
        column.addView(middle,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout coverButton=new FrameLayout(this);
        coverButton.setContentDescription("Play or pause audiobook");
        coverButton.setClickable(true);
        coverButton.setFocusable(true);
        coverButton.setOnClickListener(v->{
            PlaybackService p=PlaybackService.instance;
            if(p!=null)p.toggle();
            refresh();
        });
        // Keep a square regardless of artwork, scaled to fit both screen width
        // and the available vertical player space.
        // The artwork must itself be the hit target, not just the icon.
        // Match its bounds to the entire available square and keep the icon
        // and artwork non-clickable so touches reach the parent.
        middle.addView(coverButton,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        middle.addOnLayoutChangeListener((v,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int side=Math.min(right-left,bottom-topEdge);
            FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(side,side,Gravity.CENTER);
            coverButton.setLayoutParams(size);
        });
        ImageView largeCover=new ImageView(this);
        largeCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        largeCover.setClickable(false);
        largeCover.setFocusable(false);
        largeCover.setAlpha(0.48f);
        boolean hasArt=false;
        if(!cover.isEmpty()){
            try{largeCover.setImageURI(Uri.parse(cover));hasArt=largeCover.getDrawable()!=null;}catch(Exception ignored){}
        }
        if(!hasArt){
            MediaMetadataRetriever mmr=new MediaMetadataRetriever();
            try{
                mmr.setDataSource(this,Uri.parse(playback.uri));
                byte[] embedded=mmr.getEmbeddedPicture();
                if(embedded!=null&&embedded.length>0){
                    largeCover.setImageBitmap(BitmapFactory.decodeByteArray(embedded,0,embedded.length));
                    hasArt=largeCover.getDrawable()!=null;
                }
            }catch(Exception ignored){}
            finally{try{mmr.release();}catch(Exception ignored){}}
        }
        if(hasArt){
            coverButton.addView(largeCover,new FrameLayout.LayoutParams(-1,-1));
        }else{
            GradientDrawable outline=new GradientDrawable();
            outline.setColor(Color.TRANSPARENT);
            outline.setStroke(dp(3),0xAADFD0F6);
            outline.setCornerRadius(dp(3));
            coverButton.setBackground(outline);
        }
        play=label(playback.isPlaying()?"❚❚":"▶",96,true);
        play.setContentDescription("Play or pause audiobook");
        play.setClickable(false);
        play.setEnabled(false);
        play.setFocusable(false);
        play.setBackground(bg(0x557F64B7,100));
        coverButton.addView(play,new FrameLayout.LayoutParams(dp(200),dp(200),Gravity.CENTER));
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
        if(!displayedUri.equals(p.uri)){recreate();return;}
        if(play!=null)play.setText(p.isPlaying()?"❚❚":"▶");
        if(microphone!=null){
            boolean enabled=HandsFreeService.serviceActive;
            microphone.setAlpha(1f);
            microphone.setText(enabled?"🎙":"🎙");
            microphone.setContentDescription(enabled?"Voice activation on. Tap to stop.":"Voice activation off. Tap to start.");
            microphone.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0);
            microphone.setForeground(enabled?null:new android.graphics.drawable.Drawable(){
                private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override public void draw(android.graphics.Canvas canvas){
                    paint.setColor(0xFFFF3B30);paint.setStrokeWidth(dp(5));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    float x=getBounds().centerX(),y=getBounds().centerY(),size=dp(17);
                    canvas.drawLine(x-size,y-size,x+size,y+size,paint);
                    canvas.drawLine(x-size,y+size,x+size,y-size,paint);
                }
                @Override public void setAlpha(int a){paint.setAlpha(a);}
                @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
                @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
            });
        }
        if(elapsed!=null)elapsed.setText(format(p.position()));
        if(remaining!=null)remaining.setText(format(p.duration()));
        if(seek!=null&&!dragging){seek.setMax(Math.max(1,p.duration()));seek.setProgress(p.position());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==300&&results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){
            if(bluetoothConnected)bluetoothStartedVoice=true;
            try{startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));}
            catch(Exception e){android.widget.Toast.makeText(this,"Microphone unavailable: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
        }
    }
    @Override protected void onResume(){super.onResume();if(audioManager!=null)audioManager.registerAudioDeviceCallback(bluetoothWatcher,handler);syncBluetooth();handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);if(audioManager!=null)audioManager.unregisterAudioDeviceCallback(bluetoothWatcher);super.onPause();}
}
