package com.cosmerearchives.audiobookresearcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

/** Controlled on-device wake-phrase trials; never records or saves bookmark notes. */
public class RecognitionTestActivity extends Activity {
    private static final int MIC_REQUEST=401;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status,stats,level,engine,phraseView;
    private Button start,attempt;
    private SeekBar sensitivity;
    private boolean running=false,startedService=false,wasServiceActive=false,previousTestMode=false;
    private boolean pendingAttempt=false;
    private int successes=0,misses=0,unexpected=0,observed=0;
    private long attemptStart=0;
    private final Runnable update=new Runnable(){@Override public void run(){
        int count=prefs.getInt("wakeDetections",0);
        if(running && count>observed){
            int newDetections=count-observed;
            if(pendingAttempt && SystemClock.elapsedRealtime()-attemptStart<=7000){
                successes++;
                pendingAttempt=false;
                status.setText("✓ Recognized! Try another phrase.");
                newDetections--;
            }
            if(newDetections>0)unexpected+=newDetections;
        }
        observed=count;
        if(running && pendingAttempt && SystemClock.elapsedRealtime()-attemptStart>7000){
            pendingAttempt=false;
            misses++;
            status.setText("Not detected within 7 seconds. Try again.");
        }
        int trials=successes+misses;
        stats.setText("Recognized: "+successes+"   Missed: "+misses+
            "\nSuccess rate: "+(trials==0?"—":(successes*100/trials)+"%")+
            "\nUnexpected detections: "+unexpected);
        level.setText("Microphone level: "+prefs.getInt("wakeMicLevel",0)+" / 100");
        String lastError=prefs.getString("wakeLastError","");
        engine.setText("Engine: "+HandsFreeService.listeningState+
            (lastError.isEmpty()?"":"\nError: "+lastError));
        handler.postDelayed(this,250);
    }};

    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xFF3E3C3A);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String label){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(17);
        b.setMinHeight(dp(58));return b;
    }
    private void gap(LinearLayout parent,int value){TextView spacer=text("",1,false);parent.addView(spacer,new LinearLayout.LayoutParams(1,dp(value)));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("library",MODE_PRIVATE);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF7F6F2);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(22),dp(20),dp(30));scroll.addView(content);
        Button back=button("‹ Back to Options");content.addView(back);back.setOnClickListener(v->finish());
        gap(content,18);
        content.addView(text("Wake phrase recognition test",24,true));
        phraseView=text("Say: "+prefs.getString("wakePhrase","Hey Bookmark"),19,true);
        gap(content,16);content.addView(phraseView);
        gap(content,10);
        content.addView(text("Tap Try phrase, then say your wake phrase once within seven seconds. The app records success or a miss, without recording a note. Test in a quiet room first, then with your usual listening setup.",14,false));
        gap(content,20);
        start=button("Start test");content.addView(start);start.setOnClickListener(v->{
            if(running)stopTest();
            else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);
            else startTest();
        });
        gap(content,10);
        attempt=button("Try phrase");attempt.setEnabled(false);content.addView(attempt);
        attempt.setOnClickListener(v->{
            if(!running)return;
            if(pendingAttempt){misses++;pendingAttempt=false;}
            observed=prefs.getInt("wakeDetections",0);
            pendingAttempt=true;attemptStart=SystemClock.elapsedRealtime();
            status.setText("Listening for your phrase…");
        });
        gap(content,12);
        status=text("Start the test to begin.",16,true);content.addView(status);
        gap(content,16);
        stats=text("",16,true);content.addView(stats);
        gap(content,12);
        level=text("",14,false);content.addView(level);
        engine=text("",13,false);content.addView(engine);
        gap(content,20);
        content.addView(text("Recognition sensitivity",16,true));
        sensitivity=new SeekBar(this);sensitivity.setMax(100);
        sensitivity.setProgress(prefs.getInt("wakeSensitivity",60));content.addView(sensitivity);
        TextView sensitivityLabel=text("Sensitivity: "+sensitivity.getProgress()+"%",14,false);
        content.addView(sensitivityLabel);
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){
                sensitivityLabel.setText("Sensitivity: "+value+"%");
                if(user)prefs.edit().putInt("wakeSensitivity",value).apply();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){if(running)requestReload();}
        });
        gap(content,12);
        content.addView(text("Higher sensitivity may catch more spoken phrases, but also increases false activations. Compare results using the same microphone and listening conditions.",13,false));
        setContentView(scroll);
        observed=prefs.getInt("wakeDetections",0);
        handler.post(update);
    }
    private void requestReload(){
        if(HandsFreeService.serviceActive)
            startService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.RELOAD));
    }
    private void startTest(){
        if(running)return;
        running=true;successes=0;misses=0;unexpected=0;pendingAttempt=false;
        observed=prefs.getInt("wakeDetections",0);
        previousTestMode=prefs.getBoolean("wakeTestMode",false);
        wasServiceActive=HandsFreeService.serviceActive;
        startedService=!wasServiceActive;
        prefs.edit().putBoolean("wakeTestMode",true).apply();
        try{
            if(wasServiceActive)requestReload();
            else startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));
            start.setText("Stop test");attempt.setEnabled(true);
            status.setText("Test running. Tap Try phrase.");
        }catch(Exception e){
            stopTest();
            status.setText("Could not start microphone: "+e.getMessage());
        }
    }
    private void stopTest(){
        if(!running)return;
        running=false;pendingAttempt=false;
        prefs.edit().putBoolean("wakeTestMode",previousTestMode).apply();
        if(startedService)stopService(new Intent(this,HandsFreeService.class));
        else if(wasServiceActive)requestReload();
        startedService=false;
        start.setText("Start test");attempt.setEnabled(false);
        status.setText("Test stopped. Results remain on this screen.");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==MIC_REQUEST && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startTest();
        else if(request==MIC_REQUEST)status.setText("Microphone permission is required for recognition testing.");
    }
    @Override protected void onDestroy(){
        stopTest();handler.removeCallbacks(update);super.onDestroy();
    }
}
