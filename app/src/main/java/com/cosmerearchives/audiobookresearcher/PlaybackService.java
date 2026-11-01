package com.cosmerearchives.audiobookresearcher;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.view.KeyEvent;

public class PlaybackService extends Service {
    static final String CHANNEL="audiobook_playback";
    static final String ACTION_TOGGLE="com.cosmerearchives.audiobookresearcher.TOGGLE";
    static final String ACTION_BACK="com.cosmerearchives.audiobookresearcher.BACK";
    static final String ACTION_FORWARD="com.cosmerearchives.audiobookresearcher.FORWARD";
    static final String ACTION_STOP="com.cosmerearchives.audiobookresearcher.STOP";
    static PlaybackService instance;
    MediaPlayer player;
    String uri="", title="";
    private final IBinder binder=new LocalBinder();
    private MediaSession session;
    private AudioManager audio;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private boolean resumeOnFocus;
    private final Handler wrapHandler=new Handler(Looper.getMainLooper());
    private void storeProgress(boolean complete){
        try{
            android.content.SharedPreferences prefs=getSharedPreferences("library",0);
            org.json.JSONArray books=new org.json.JSONArray(prefs.getString("books","[]"));
            for(int i=0;i<books.length();i++){
                org.json.JSONObject item=books.optJSONObject(i);
                if(item!=null&&uri.equals(item.optString("uri"))){
                    item.put("position",complete?duration():position());
                    item.put("started",true);
                    item.put("completed",complete);
                    break;
                }
            }
            prefs.edit().putString("books",books.toString()).apply();
        }catch(Exception ignored){}
    }

    private final Runnable wrappingTicker=new Runnable(){public void run(){
        if(player!=null&&isPlaying()){
            storeProgress(false);
            int at=position(),adjusted=BookOrganizer.skipPosition(PlaybackService.this,uri,at,duration());
            if(adjusted>=duration()-250&&adjusted>at)advance();
            else if(adjusted>at+100)seek(adjusted);
        }
        wrapHandler.postDelayed(this,500);
    }};
    private void advance(){
        storeProgress(true);
        String next=BookOrganizer.next(this,uri);
        if(next==null){pause();return;}
        try{
            org.json.JSONArray all=new org.json.JSONArray(getSharedPreferences("library",0).getString("books","[]"));
            String name=next;
            for(int i=0;i<all.length();i++){
                org.json.JSONObject book=all.optJSONObject(i);
                if(book!=null&&next.equals(book.optString("uri"))){name=book.optString("title",next);break;}
            }
            open(next,name,0);
        }catch(Exception e){pause();}
    }

    static volatile String lastMediaButton="No button detected yet";
    static volatile long lastMediaButtonAt=0;
    private void logMediaButton(String value){lastMediaButton=value;lastMediaButtonAt=System.currentTimeMillis();}
    class LocalBinder extends Binder { PlaybackService service(){return PlaybackService.this;} }

    @Override public void onCreate() {
        super.onCreate();
        instance=this;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Audiobook playback",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focusListener=change->{
            if(player==null)return;
            if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeOnFocus=player.isPlaying();pause();}
            else if(change==AudioManager.AUDIOFOCUS_LOSS){resumeOnFocus=false;pause();}
            else if(change==AudioManager.AUDIOFOCUS_GAIN&&resumeOnFocus){resumeOnFocus=false;play();}
        };
        session=new MediaSession(this,"AudiobookResearcher");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){logMediaButton("MediaSession: Play");play();}
            @Override public void onPause(){logMediaButton("MediaSession: Pause");pause();}
            @Override public void onSkipToPrevious(){logMediaButton("MediaSession: Previous");seek(position()-15000);}
            @Override public void onSkipToNext(){logMediaButton("MediaSession: Next");seek(position()+30000);}
            @Override public void onSeekTo(long p){seek((int)p);}
            @Override public boolean onMediaButtonEvent(Intent intent){
                KeyEvent key=intent==null?null:intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(key!=null && key.getAction()==KeyEvent.ACTION_DOWN && key.getRepeatCount()==0){
                    logMediaButton(KeyEvent.keyCodeToString(key.getKeyCode()));
                    switch(key.getKeyCode()){
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                            toggle();return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            play();return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            pause();return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            seek(position()+30000);return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            seek(position()-15000);return true;
                        default:break;
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
        wrapHandler.postDelayed(wrappingTicker,500);
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null){
            String action=intent.getAction();
            if(ACTION_TOGGLE.equals(action))toggle();
            else if(ACTION_BACK.equals(action))seek(position()-15000);
            else if(ACTION_FORWARD.equals(action))seek(position()+30000);
            else if(ACTION_STOP.equals(action)){stopPlayback();stopSelf();return START_NOT_STICKY;}
        }
        return START_NOT_STICKY;
    }
    void open(String source,String name,int at)throws Exception{
        if(player!=null){player.release();player=null;}
        uri=source;title=name;
        getSharedPreferences("library",0).edit().putString("lastBookUri",source).apply();
        MediaPlayer next=new MediaPlayer();
        next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        try{
            next.setDataSource(this,Uri.parse(source));
            next.prepare();
            next.seekTo(Math.min(Math.max(0,at),next.getDuration()));
            next.setOnCompletionListener(p->advance());
            player=next;
            play();
        }catch(Exception ex){next.release();throw ex;}
    }
    int position(){try{return player==null?0:player.getCurrentPosition();}catch(Exception e){return 0;}}
    int duration(){try{return player==null?0:player.getDuration();}catch(Exception e){return 0;}}
    boolean isPlaying(){try{return player!=null&&player.isPlaying();}catch(Exception e){return false;}}
    void seek(int p){if(player!=null){player.seekTo(Math.max(0,Math.min(p,duration())));refresh();}}
    void play(){
        if(player==null)return;
        audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        player.start();refresh();
    }
    void pause(){if(player!=null&&player.isPlaying()){player.pause();storeProgress(false);}refresh();}
    void toggle(){if(isPlaying())pause();else play();}
    private PendingIntent action(String name,int request){
        Intent i=new Intent(this,PlaybackService.class).setAction(name);
        return PendingIntent.getService(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void refresh(){
        if(player==null)return;
        int flags=PlaybackState.STATE_PAUSED;
        if(isPlaying())flags=PlaybackState.STATE_PLAYING;
        long commands=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|
            PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder().setActions(commands).setState(flags,position(),isPlaying()?1:0).build());
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());
        Intent open=new Intent(this,NowPlayingActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Action back=new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Back 15 seconds",action(ACTION_BACK,1)).build();
        Notification.Action toggle=new Notification.Action.Builder(isPlaying()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
             isPlaying()?"Pause":"Play",action(ACTION_TOGGLE,2)).build();
        Notification.Action forward=new Notification.Action.Builder(android.R.drawable.ic_media_next,"Forward 30 seconds",action(ACTION_FORWARD,3)).build();
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title).setContentText(isPlaying()?"Playing audiobook":"Playback paused")
            .setContentIntent(content).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(back).addAction(toggle).addAction(forward)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2))
            .setOngoing(isPlaying()).build();
        startForeground(42,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }
    private void stopPlayback(){
        if(player!=null){player.release();player=null;}
        audio.abandonAudioFocus(focusListener);
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy(){wrapHandler.removeCallbacks(wrappingTicker);stopPlayback();session.release();instance=null;super.onDestroy();}
}
