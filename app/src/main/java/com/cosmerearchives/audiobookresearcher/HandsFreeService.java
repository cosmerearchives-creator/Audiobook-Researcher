package com.cosmerearchives.audiobookresearcher;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.media.session.MediaSession;
import android.os.*;
import android.speech.*;
import org.json.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class HandsFreeService extends Service {
    public static final String START="com.cosmerearchives.audiobookresearcher.HANDS_FREE_START";
    public static final String STOP="com.cosmerearchives.audiobookresearcher.HANDS_FREE_STOP";
    private static final String CHANNEL="hands_free";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private MediaRecorder recorder;
    private File audioFile;
    private boolean enabled, listening, recording, heardVoice, resume;
    static volatile boolean serviceActive=false;
    static volatile String listeningState="Off";
    private long speechAt, recordingStart;
    private String bookUri;
    private int timestamp;
    private final Runnable restart=()->listen();
    private final Runnable monitor=new Runnable(){
        @Override public void run(){
            if(!recording || recorder==null)return;
            try {
                long now=SystemClock.elapsedRealtime();
                int amplitude=recorder.getMaxAmplitude();
                if(amplitude>1600){heardVoice=true;speechAt=now;}
                if(heardVoice&&now-speechAt>=4000){finishNote(true);return;}
            }catch(Exception ignored){}
            handler.postDelayed(this,500);
        }
    };
    @Override public void onCreate(){
        super.onCreate();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel(CHANNEL,"Hands-free voice listening",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){shutdown();stopSelf();return START_NOT_STICKY;}
        if(!enabled){
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                stopSelf();return START_NOT_STICKY;
            }
            enabled=true;
            serviceActive=true;listeningState="Starting";
            getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("backgroundHandsFree",true).apply();
            showNotification("Offline listening for Bookmark");
            listen();
        }
        return START_NOT_STICKY;
    }
    private PendingIntent stopAction(){
        Intent intent=new Intent(this,HandsFreeService.class).setAction(STOP);
        return PendingIntent.getService(this,3,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void showNotification(String state){
        Notification note=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Audiobook Researcher • Hands-free mode")
            .setContentText(state).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop listening",stopAction()).build())
            .build();
        startForeground(43,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }
    private OfflineBookmarkDetector detector;
    private boolean ducked;
    private long recentVoice;
    private long lastDuckAt;
    private final Runnable restoreVolume=()->setDuck(false);
    private void setDuck(boolean value){
        if(ducked==value)return;
        ducked=value;
        PlaybackService p=PlaybackService.instance;
        if(p!=null)p.setHandsFreeDuck(value);
        getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("wakeDucked",value).apply();
    }
    private void receivedLevel(int level){
        getSharedPreferences("library",MODE_PRIVATE).edit().putInt("wakeMicLevel",level).apply();
        long now=SystemClock.elapsedRealtime();
        // Gate on sustained, relatively strong audio so narration does not duck continuously.
        int gate=getSharedPreferences("library",MODE_PRIVATE).getInt("wakeVoiceGate",30);
        if(level>=gate){
            if(recentVoice==0)recentVoice=now;
            if(now-recentVoice>=300 && now-lastDuckAt>=2500){
                setDuck(true);lastDuckAt=now;
            }
            handler.removeCallbacks(restoreVolume);
            handler.postDelayed(restoreVolume,1500);
        }else if(now-recentVoice>1600)recentVoice=0;
    }
    private void listen(){
        if(!enabled||listening||recording)return;
        listening=true;
        listeningState="Listening";
        showNotification("Offline listening for Bookmark");
        detector=new OfflineBookmarkDetector(this,
            ()->{listening=false;capture();},
            error->{listening=false;listeningState="Error: "+error;setDuck(false);showNotification("Microphone error: "+error);scheduleRestart();},
            level->receivedLevel(level)
        );
        detector.start();
    }
    private void scheduleRestart(){
        handler.removeCallbacks(restart);
        if(enabled)handler.postDelayed(restart,1200);
    }
    private void capture(){
        PlaybackService playback=PlaybackService.instance;
        if(playback==null||playback.player==null){scheduleRestart();return;}
        bookUri=playback.uri;timestamp=playback.position();resume=playback.isPlaying();
        setDuck(false);
        android.content.SharedPreferences debug=getSharedPreferences("library",MODE_PRIVATE);
        debug.edit().putInt("wakeDetections",debug.getInt("wakeDetections",0)+1).apply();
        if(debug.getBoolean("wakeTestMode",false)){
            listening=false;
            detector=null;
            listeningState="Test phrase detected";
            showNotification("Test detection succeeded • listening");scheduleRestart();return;
        }
        listeningState="Recording note";
        playback.pause();
        if(detector!=null){detector.stop();detector=null;}
        listening=false;
        File dir=new File(getFilesDir(),"voice_notes");
        if(!dir.exists()&&!dir.mkdirs()){endWithError();return;}
        audioFile=new File(dir,"handsfree_"+System.currentTimeMillis()+".m4a");
        try{
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();recorder.start();
            recording=true;heardVoice=false;
            recordingStart=SystemClock.elapsedRealtime();speechAt=recordingStart;
            showNotification("Recording bookmark • 4 seconds quiet to save");
            handler.postDelayed(monitor,500);
        }catch(Exception e){endWithError();}
    }
    private void endWithError(){finishNote(false);}
    private void finishNote(boolean keep){
        handler.removeCallbacks(monitor);
        boolean valid=true;
        if(recorder!=null){
            try{recorder.stop();}catch(Exception e){valid=false;}
            recorder.release();recorder=null;
        }
        recording=false;
        if(keep&&valid&&audioFile!=null&&audioFile.exists()&&audioFile.length()>0){
            android.content.SharedPreferences prefs=getSharedPreferences("library",MODE_PRIVATE);
            try{
                JSONArray notes=new JSONArray(prefs.getString("notes","[]"));
                JSONObject note=new JSONObject();
                note.put("bookUri",bookUri);
                note.put("body","Voice note • tap to listen");
                note.put("position",timestamp);
                note.put("audioPath",audioFile.getAbsolutePath());
                notes.put(note);
                prefs.edit().putString("notes",notes.toString()).apply();
            }catch(Exception ignored){}
        }else if(audioFile!=null)audioFile.delete();
        PlaybackService p=PlaybackService.instance;
        setDuck(false);
        if(resume&&p!=null)p.play();
        resume=false;
        if(enabled){
            listeningState="Restarting";
            showNotification(keep&&valid?"Bookmark saved • listening":"Listening for Bookmark");
            scheduleRestart();
        }
    }
    private void shutdown(){
        enabled=false;
        serviceActive=false;listeningState="Off";
        setDuck(false);
        handler.removeCallbacks(restoreVolume);
        getSharedPreferences("library",MODE_PRIVATE).edit().putBoolean("backgroundHandsFree",false).apply();
        handler.removeCallbacks(restart);handler.removeCallbacks(monitor);
        if(detector!=null){detector.stop();detector=null;}
        listening=false;
        if(recording)finishNote(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy(){shutdown();super.onDestroy();}
}
