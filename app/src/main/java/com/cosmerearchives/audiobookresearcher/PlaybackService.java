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
    private float handsFreeVolume=1f;
    void setHandsFreeDuck(boolean duck){
        handsFreeVolume=duck?0.2f:1f;
        if(player!=null)player.setVolume(handsFreeVolume,handsFreeVolume);
    }
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
            @Override public void onPlay(){play();}
            @Override public void onPause(){pause();}
            @Override public void onSkipToPrevious(){seek(position()-15000);}
            @Override public void onSkipToNext(){seek(position()+30000);}
            @Override public void onSeekTo(long p){seek((int)p);}
            @Override public boolean onMediaButtonEvent(Intent intent){
                KeyEvent key=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(key!=null&&key.getAction()==KeyEvent.ACTION_DOWN){
                    int code=key.getKeyCode();
                    if(code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||code==KeyEvent.KEYCODE_HEADSETHOOK){toggle();return true;}
                }
                return super.onMediaButtonEvent(intent);
            }
        });
        session.setActive(true);
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
        MediaPlayer next=new MediaPlayer();
        next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        try{
            next.setDataSource(this,Uri.parse(source));
            next.prepare();
            next.seekTo(Math.min(Math.max(0,at),next.getDuration()));
            next.setOnCompletionListener(p->{pause();seek(0);});
            player=next;
            next.setVolume(handsFreeVolume,handsFreeVolume);
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
    void pause(){if(player!=null&&player.isPlaying())player.pause();refresh();}
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
    @Override public void onDestroy(){stopPlayback();session.release();instance=null;super.onDestroy();}
}
