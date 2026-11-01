package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.media.MediaRecorder;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.util.ArrayList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.graphics.BitmapFactory;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 200;
    private static final int PICK_AUDIOBOOK_FOLDER = 205;
    private static final int PICK_COVER_FOLDER = 204;
    private static final int DICTATE_NOTE = 201;
    private static final int MIC_PERMISSION = 202;
    private static final int WAKE_PERMISSION = 203;
    private boolean backgroundHandsFree;
    private String section="library";
    private Runnable buttonTestTicker;
    private MediaPlayer notePlayer;
    private Note playingNote;
    private Runnable noteTicker;
    private TextView notePlayButton;
    private void stopNotePlayer(){
        if(noteTicker!=null)handler.removeCallbacks(noteTicker);
        noteTicker=null;
        if(notePlayer!=null){try{notePlayer.release();}catch(Exception ignored){}notePlayer=null;}
        if(notePlayButton!=null)notePlayButton.setText("▶");
        notePlayButton=null;playingNote=null;
    }
    private String clipTime(int millis){
        int secs=Math.max(0,millis/1000);
        return String.format(java.util.Locale.US,"%d:%02d",secs/60,secs%60);
    }
    private void toggleNoteClip(Note note,TextView playControl,TextView timer){
        if(notePlayer!=null && playingNote==note){
            if(notePlayer.isPlaying()){notePlayer.pause();playControl.setText("▶");}
            else{notePlayer.start();playControl.setText("❚❚");}
            return;
        }
        stopNotePlayer();
        try{
            MediaPlayer mp=new MediaPlayer();
            mp.setDataSource(note.audioPath);mp.prepare();
            notePlayer=mp;playingNote=note;notePlayButton=playControl;
            mp.setOnCompletionListener(done->{
                if(notePlayer!=done)return;
                done.seekTo(0);
                playControl.setText("▶");
                timer.setText("0:00 / "+clipTime(done.getDuration()));
            });
            timer.setText("0:00 / "+clipTime(mp.getDuration()));
            mp.start();playControl.setText("❚❚");
            noteTicker=new Runnable(){public void run(){
                if(notePlayer!=mp)return;
                timer.setText(clipTime(mp.getCurrentPosition())+" / "+clipTime(mp.getDuration()));
                handler.postDelayed(this,250);
            }};
            handler.post(noteTicker);
        }catch(Exception e){
            stopNotePlayer();
            new android.app.AlertDialog.Builder(this).setMessage("Unable to play voice note: "+e.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void deleteVoiceNote(Note note){
        new android.app.AlertDialog.Builder(this).setTitle("Delete voice note?")
            .setMessage("This permanently deletes the saved recording and bookmark.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(dialog,which)->{
                if(playingNote==note)stopNotePlayer();
                notes.remove(note);saveNotes();
                if(note.audioPath!=null&&!note.audioPath.isEmpty())new File(note.audioPath).delete();
                render();
            }).show();
    }

    private SpeechRecognizer wakeRecognizer;
    private boolean wakeEnabled=false;
    private boolean wakeRunning=false;
    private TextView wakeToggle;
    private final Runnable restartWake=()->listenForWake();
    private MediaRecorder recorder;
    private File recordingFile;
    private long lastVoice, recordingStarted;
    private boolean heardVoice;
    private android.app.AlertDialog recordingDialog;
    private TextView recordingStatus;
    private TextToSpeech tts;
    private boolean recording;
    private final ArrayList<Note> notes = new ArrayList<>();
    private String pendingBookUri;
    private int pendingPosition;
    private boolean resumeAfterNote;
    private static final int PURPLE = Color.rgb(105,92,137);
    private static final int INK = Color.rgb(62,60,58);
    private static final int BG = Color.rgb(247,246,242);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.ArrayList<Book> books = new java.util.ArrayList<>();
    private SharedPreferences prefs;
    private MediaPlayer player;
    private PlaybackService playback;
    private boolean bound;
    private final android.content.ServiceConnection serviceConnection=new android.content.ServiceConnection(){
        @Override public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){
            playback=((PlaybackService.LocalBinder)binder).service();bound=true;
            boolean launchPlayer=getIntent().getBooleanExtra("openPlayer",false) || !getIntent().hasExtra("section");
            if(playback.player==null&&launchPlayer&&!books.isEmpty()){
                try{
                    Book book=books.get(0);active=book;
                    playback.open(book.uri,book.title,book.position);
                    playback.pause();
                }catch(Exception ignored){}
            }
            if(playback.player!=null){
                player=playback.player;
                for(Book book:books)if(book.uri.equals(playback.uri)){active=book;break;}
                render();
                if(launchPlayer)startActivity(new Intent(MainActivity.this,NowPlayingActivity.class));
            }
        }
        @Override public void onServiceDisconnected(android.content.ComponentName name){bound=false;playback=null;player=null;}
    };
    private Book active;
    private TextView status, progress, playButton, currentBook;
    private ProgressBar updateProgress;
    private TextView updateDetails, installUpdate;
    private SeekBar seek;
    private boolean tracking;
    private UpdateManager updater;

    private static class Book {
        String uri, title, coverUri="", folderUri="", album="", artist="", track=""; int position;
        Book(String u, String t, int p) { uri=u; title=t; position=p; }
    }

    private static class Note {
        String bookUri, body, audioPath; int position;
        Note(String u, String b, int p) {this(u,b,p,"");}
        Note(String u, String b, int p,String a) { bookUri=u; body=b; position=p; audioPath=a; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("library", MODE_PRIVATE);
        section=getIntent().getStringExtra("section");
        if(section==null)section="library";
        restoreBooks();
        restoreNotes();
        backgroundHandsFree=HandsFreeService.serviceActive;
        tts=new TextToSpeech(this,result->{});
        render();
        bindService(new Intent(this,PlaybackService.class),serviceConnection,BIND_AUTO_CREATE);
        handler.postDelayed(ticker, 1000);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null && active != null) {
                try {
                    int position = player.getCurrentPosition();
                    active.position = position;
                    if (seek != null && !tracking) seek.setProgress(position);
                    if (progress != null) progress.setText(format(position) + " / " + format(player.getDuration()));
                    if (player.isPlaying()) saveBooks();
                } catch (IllegalStateException ignored) {}
            }
            if(recording)monitorRecording();
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) { return (int)(getResources().getDisplayMetrics().density*v + .5f); }
    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(INK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout l, int height) {
        View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1,dp(height)));
    }
    private TextView button(String label, boolean primary) {
        TextView b = text(label, 15, true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(16),dp(13),dp(16),dp(13));
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setBackground(shape(primary ? PURPLE : Color.WHITE, 14, primary ? 0 : 0xFFDCDAD6));
        return b;
    }
    private void render() {
        if(notePlayer!=null)stopNotePlayer();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(28),dp(20),dp(30)); scroll.addView(root);
        TextView toPlayer=button("‹  Now Playing",false);
        root.addView(toPlayer);toPlayer.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));
        space(root,15);
        TextView heading = text("Audiobook Researcher", 28, true); root.addView(heading);
        space(root,7);
        TextView sub = text(section.equals("notes")?"Your bookmarked notes":section.equals("options")?"App & voice activation settings":section.equals("tools")?"Diagnostics and utilities":"Your listening library",15,false);
        sub.setTextColor(0xFF807D77); root.addView(sub);
        space(root,12);
        if(section.equals("library")){
        if(active!=null&&player!=null){
            TextView nowPlaying=button("▶  Return to full-screen player",true);
            nowPlaying.setTextSize(19);
            root.addView(nowPlaying,new LinearLayout.LayoutParams(-1,dp(78)));
            nowPlaying.setOnClickListener(v->startActivity(new Intent(this,NowPlayingActivity.class)));
            space(root,10);
            TextView coverSelect=button(active.coverUri.isEmpty()?"Select audiobook artwork folder":"Change artwork folder",false);
            root.addView(coverSelect);
            coverSelect.setOnClickListener(v->{
                Intent folder=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                folder.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(folder,PICK_COVER_FOLDER);
            });
            space(root,18);
        }
        TextView importButton=button(prefs.getString("audiobookFolder","").isEmpty()?
            "Choose Audiobooks folder":"Change Audiobooks folder",true);
        root.addView(importButton);
        importButton.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,PICK_AUDIOBOOK_FOLDER);
        });
        space(root,10);
        if(!prefs.getString("audiobookFolder","").isEmpty()){
            TextView scanButton=button("↻  Scan Audiobooks folder",false);
            root.addView(scanButton);
            scanButton.setOnClickListener(v->scanAudiobookFolder(Uri.parse(prefs.getString("audiobookFolder",""))));
        }
        space(root,22);
        TextView libraryTitle = text("Your books",21,true); root.addView(libraryTitle);
        space(root,12);
        if (books.isEmpty()) {
            TextView empty = text("No audiobooks yet. Import an MP3, M4B, or another audio file to start listening.",15,false);
            empty.setPadding(dp(16),dp(22),dp(16),dp(22));
            empty.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            root.addView(empty);
        }
        java.util.ArrayList<Book> sortedBooks=new java.util.ArrayList<>(books);
        java.util.Collections.sort(sortedBooks,(a,b)->{
            String keyA=a.album.isEmpty()?"~"+a.title:a.album.toLowerCase(java.util.Locale.ROOT)+"|"+a.artist.toLowerCase(java.util.Locale.ROOT);
            String keyB=b.album.isEmpty()?"~"+b.title:b.album.toLowerCase(java.util.Locale.ROOT)+"|"+b.artist.toLowerCase(java.util.Locale.ROOT);
            int c=keyA.compareTo(keyB);
            if(c!=0)return c;
            try{return Integer.compare(Integer.parseInt(a.track.replaceAll("[^0-9].*","")),Integer.parseInt(b.track.replaceAll("[^0-9].*","")));}catch(Exception ignored){return a.title.compareToIgnoreCase(b.title);}
        });
        String previousAlbum="";
        for (Book book : sortedBooks) {
            String group=book.album.trim().isEmpty()?"":book.album+"|"+book.artist;
            if(!group.isEmpty()&&!group.equals(previousAlbum)){
                TextView groupLabel=text(book.album+(book.artist.isEmpty()?"":"  •  "+book.artist),19,true);
                root.addView(groupLabel);space(root,10);
            }
            previousAlbum=group;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16),dp(14),dp(16),dp(14)); row.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            TextView title = text(book.title,17,true); row.addView(title);
            space(row,5);
            TextView detail = text((book.track.isEmpty()?"":"Part/track "+book.track+"  •  ")+"Resume at " + format(book.position),13,false);
            detail.setTextColor(0xFF817E78); row.addView(detail);
            root.addView(row); space(root,10);
            row.setOnClickListener(v -> openBook(book));
        }
        space(root,14);
        }
        if(section.equals("options")){
        {
            TextView diagnostics=button("Wake detection settings & test",false);
            root.addView(diagnostics);
            diagnostics.setOnClickListener(v->openWakeDiagnostics());
            space(root,10);
            TextView phraseTitle=text("Wake phrase",17,true);
            root.addView(phraseTitle);
            space(root,5);
            String phrase=prefs.getString("wakePhrase","Hey Bookmark");
            TextView phraseButton=button("“"+phrase+"”  •  Edit phrase",false);
            root.addView(phraseButton);
            phraseButton.setOnClickListener(v->editWakePhrase());
            space(root,10);
            wakeToggle=button(backgroundHandsFree?"Stop hands-free mode":"Start hands-free mode (experimental)",false);
            root.addView(wakeToggle);
            wakeToggle.setOnClickListener(v->{
                if(backgroundHandsFree){
                    stopService(new Intent(this,HandsFreeService.class));
                    backgroundHandsFree=false;
                    prefs.edit().putBoolean("backgroundHandsFree",false).apply();
                    wakeToggle.setText("Start hands-free mode (experimental)");
                } else startBackgroundHandsFree();
            });
            space(root,18);
        }
        }
        if(section.equals("notes")){
        if(active!=null&&player!=null){
            TextView addNote=button("+ Add bookmarked note",true);
            root.addView(addNote);addNote.setOnClickListener(v->startNote());space(root,16);
        }
        TextView notesHeading=text("Bookmarked notes",21,true);root.addView(notesHeading);
        space(root,10);
        int noteCount=0;
        for(int i=notes.size()-1;i>=0;i--) {
            Note n=notes.get(i);
            if(active==null || !n.bookUri.equals(active.uri))continue;
            noteCount++;
            LinearLayout noteRow=new LinearLayout(this);
            noteRow.setOrientation(LinearLayout.VERTICAL);
            noteRow.setPadding(dp(15),dp(13),dp(15),dp(13));
            noteRow.setBackground(shape(Color.WHITE,14,0xFFE0DDD8));
            TextView timestamp=text("BOOKMARK  •  "+format(n.position),12,true);
            timestamp.setTextColor(PURPLE);noteRow.addView(timestamp);
            space(noteRow,6);
            TextView body=text(n.body,15,false);noteRow.addView(body);
            if(n.audioPath!=null&&!n.audioPath.isEmpty()){
                LinearLayout mini=new LinearLayout(this);mini.setGravity(Gravity.CENTER_VERTICAL);
                TextView listen=button("▶",false);
                listen.setContentDescription("Play or pause voice note");
                mini.addView(listen,new LinearLayout.LayoutParams(dp(64),dp(58)));
                TextView timer=text("0:00 / --:--",14,true);
                timer.setGravity(Gravity.CENTER);
                mini.addView(timer,new LinearLayout.LayoutParams(0,dp(58),1));
                TextView delete=button("Delete",false);
                delete.setTextColor(0xFFB3261E);
                mini.addView(delete,new LinearLayout.LayoutParams(dp(90),dp(58)));
                noteRow.addView(mini);
                listen.setOnClickListener(v->toggleNoteClip(n,listen,timer));
                delete.setOnClickListener(v->deleteVoiceNote(n));
                space(noteRow,8);
            }
            space(noteRow,8);
            TextView jump=button("Play from 15s before",false);
            noteRow.addView(jump);
            jump.setOnClickListener(v -> {
                seekTo(n.position-15000);
                if(player!=null&&!player.isPlaying())toggle();
            });
            root.addView(noteRow);space(root,10);
        }
        if(noteCount==0)root.addView(text(active==null?"Open an audiobook to view its notes.":"No notes for this audiobook yet.",14,false));
        space(root,18);
        }
        if(section.equals("tools")){
            TextView testerTitle=text("Bluetooth Button Tester",21,true);
            root.addView(testerTitle);space(root,8);
            TextView guidance=text("While parked, connect Bluetooth audio and press a steering-wheel button. Only media events Android delivers to the app can appear here. Phone answer and hang-up buttons may not emit media events.",15,false);
            root.addView(guidance);space(root,12);
            TextView result=text("Waiting for media button…",17,true);
            result.setPadding(dp(16),dp(22),dp(16),dp(22));
            result.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            root.addView(result);
            if(buttonTestTicker!=null)handler.removeCallbacks(buttonTestTicker);
            final Runnable[] updater=new Runnable[1];
            updater[0]=()->{
                if(!section.equals("tools"))return;
                String event=PlaybackService.lastMediaButton;
                long at=PlaybackService.lastMediaButtonAt;
                result.setText(event+(at>0?"\\nReceived: "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date(at)):""));
                handler.postDelayed(updater[0],350);
            };
            buttonTestTicker=updater[0];
            handler.post(buttonTestTicker);
            space(root,12);
            TextView testNote=text("This tester observes Android MediaSession events. It does not intercept telephone calls or remap calling buttons. Playback may respond normally to tested media controls.",13,false);
            root.addView(testNote);
        }
        if(section.equals("options")){
        TextView updaterTitle = text("App updates",20,true); root.addView(updaterTitle);
        space(root,9);
        status=text("Checking for new versions…",14,true); root.addView(status);
        space(root,8);
        updateProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        updateProgress.setProgressTintList(ColorStateList.valueOf(PURPLE));
        updateProgress.setIndeterminateTintList(ColorStateList.valueOf(PURPLE));
        updateProgress.setVisibility(View.GONE);
        root.addView(updateProgress,new LinearLayout.LayoutParams(-1,dp(8)));
        space(root,8);
        updateDetails=text("",13,false);
        updateDetails.setTextColor(0xFF807D77);root.addView(updateDetails);
        space(root,10);
        installUpdate=button("Install downloaded update",true);
        installUpdate.setVisibility(View.GONE);root.addView(installUpdate);
        space(root,10);
        TextView updateButton=button("Check for updates",false); root.addView(updateButton);
        if(updater!=null)updater.onDestroy();
        updater=new UpdateManager(this,status,updateProgress,updateDetails,installUpdate);
        updateButton.setOnClickListener(v->updater.check());
        updater.check();
        }
        setContentView(scroll);
    }
    private void openWakeDiagnostics(){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(8),dp(20),dp(12));
        TextView intro=text("Higher sensitivity catches more phrases but may cause false positives.",14,false);layout.addView(intro);
        space(layout,12);
        TextView label=text("Sensitivity: "+prefs.getInt("wakeSensitivity",60)+"%",15,true);layout.addView(label);
        SeekBar sensitivity=new SeekBar(this);sensitivity.setMax(100);
        sensitivity.setProgress(prefs.getInt("wakeSensitivity",60));layout.addView(sensitivity);
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int value,boolean user){label.setText("Sensitivity: "+value+"%");}
            public void onStopTrackingTouch(SeekBar b){}
        });
        space(layout,10);
        TextView gateLabel=text("Microphone activity diagnostics",14,true);layout.addView(gateLabel);


        TextView meter=text("Microphone level: --",14,false);layout.addView(meter);
        TextView counter=text("Successful detections: "+prefs.getInt("wakeDetections",0),14,false);layout.addView(counter);
        android.widget.CheckBox test=new android.widget.CheckBox(this);
        test.setText("Test mode: count wake words without creating notes");
        test.setChecked(prefs.getBoolean("wakeTestMode",false));layout.addView(test);
        final Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            meter.setText("Microphone level: "+prefs.getInt("wakeMicLevel",0)+" / 100");
            counter.setText("Successful detections: "+prefs.getInt("wakeDetections",0));
            handler.postDelayed(refresh[0],400);
        };
        handler.post(refresh[0]);
        new android.app.AlertDialog.Builder(this).setTitle("Wake-word diagnostics")
            .setView(layout)
            .setPositiveButton("Save",(d,w)->{
                prefs.edit().putInt("wakeSensitivity",sensitivity.getProgress())
                    .putBoolean("wakeTestMode",test.isChecked()).apply();
                handler.removeCallbacks(refresh[0]);
                if(backgroundHandsFree){
                    stopService(new Intent(this,HandsFreeService.class));
                    backgroundHandsFree=false;
                    prefs.edit().putBoolean("backgroundHandsFree",false).apply();
                    render();
                }
            }).setNegativeButton("Cancel",(d,w)->handler.removeCallbacks(refresh[0]))
            .setOnCancelListener(d->handler.removeCallbacks(refresh[0])).show();
    }
    private void editWakePhrase(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString("wakePhrase","Hey Bookmark"));
        input.setSelection(input.length());
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(dp(20),dp(10),dp(20),0);wrapper.addView(input);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Custom wake phrase")
            .setMessage("Try a distinctive 2–4 word phrase to reduce false activations. Changes apply when listening restarts.")
            .setView(wrapper)
            .setPositiveButton("Save",(dialog,which)->{
                String phrase=input.getText().toString().trim().replaceAll("\\s+"," ");
                if(!phrase.matches("[A-Za-z]+(?: [A-Za-z]+){0,4}")){
                    new android.app.AlertDialog.Builder(this)
                        .setMessage("Use 1–5 English words containing letters only.")
                        .setPositiveButton("OK",null).show();
                    return;
                }
                prefs.edit().putString("wakePhrase",phrase).apply();
                if(backgroundHandsFree){
                    stopService(new Intent(this,HandsFreeService.class));
                    backgroundHandsFree=false;
                    prefs.edit().putBoolean("backgroundHandsFree",false).apply();
                }
                render();
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void startBackgroundHandsFree(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},WAKE_PERMISSION);return;
        }
        try{
            startForegroundService(new Intent(this,HandsFreeService.class).setAction(HandsFreeService.START));
            backgroundHandsFree=true;
            prefs.edit().putBoolean("backgroundHandsFree",true).apply();
            if(wakeToggle!=null)wakeToggle.setText("Stop hands-free mode");
        }catch(Exception e){
            new android.app.AlertDialog.Builder(this).setMessage("Unable to start hands-free mode: "+e.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void startWake(){
        if(active==null || player==null)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},WAKE_PERMISSION);return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            new android.app.AlertDialog.Builder(this).setMessage("This device has no available Android speech recognizer.").setPositiveButton("OK",null).show();return;
        }
        wakeEnabled=true;
        if(wakeToggle!=null)wakeToggle.setText("Stop voice activation (experimental)");
        if(wakeRecognizer==null){
            wakeRecognizer=SpeechRecognizer.createSpeechRecognizer(this);
            wakeRecognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle params){}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rms){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){}
                @Override public void onError(int error){wakeRunning=false; if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){stopWake();return;}restartWakeSoon();}
                @Override public void onResults(Bundle result){
                    wakeRunning=false;
                    ArrayList<String> choices=result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(choices!=null)for(String candidate:choices){
                        if(java.util.Arrays.asList(candidate.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+")).contains("bookmark")){
                            stopWake();
                            beginVoiceBookmark();
                            return;
                        }
                    }
                    restartWakeSoon();
                }
                @Override public void onPartialResults(Bundle partial){}
                @Override public void onEvent(int eventType,Bundle params){}
            });
        }
        listenForWake();
    }
    private void listenForWake(){
        if(!wakeEnabled || wakeRecognizer==null || wakeRunning || recording)return;
        wakeRunning=true;
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        try{wakeRecognizer.startListening(intent);}catch(Exception ex){wakeRunning=false;restartWakeSoon();}
    }
    private void restartWakeSoon(){
        handler.removeCallbacks(restartWake);
        if(wakeEnabled)handler.postDelayed(restartWake,1200);
    }
    private void stopWake(){
        wakeEnabled=false;wakeRunning=false;
        handler.removeCallbacks(restartWake);
        if(wakeRecognizer!=null){wakeRecognizer.destroy();wakeRecognizer=null;}
        if(wakeToggle!=null)wakeToggle.setText("Enable voice activation (experimental)");
    }
    private void beginVoiceBookmark(){
        if(active==null || player==null)return;
        pendingBookUri=active.uri;
        pendingPosition=player.getCurrentPosition();
        resumeAfterNote=player.isPlaying();
        if(resumeAfterNote){if(playback!=null)playback.pause();else player.pause();}
        if(playButton!=null)playButton.setText("▶  Play");
        beginRecording();
    }
    private void startNote() {
        if(active==null||player==null)return;
        if(wakeEnabled)stopWake();
        pendingBookUri=active.uri;
        pendingPosition=player.getCurrentPosition();
        resumeAfterNote=player.isPlaying();
        if(resumeAfterNote){if(playback!=null)playback.pause();else player.pause();}
        if(playButton!=null)playButton.setText("Play");
        String[] choices={"Record voice note","Dictate note","Type note","Cancel"};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Bookmark at "+format(pendingPosition))
            .setItems(choices,(dialog,which)->{
                if(which==0)beginRecording();
                else if(which==1)dictateNote();
                else if(which==2)typeNote("");
                else resumeAfterCapture();
            }).setOnCancelListener(d -> resumeAfterCapture()).show();
    }
    private void beginRecording(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);return;
        }
        File directory=new File(getFilesDir(),"voice_notes");
        if(!directory.exists()&&!directory.mkdirs()){resumeAfterCapture();return;}
        recordingFile=new File(directory,"note_"+System.currentTimeMillis()+".m4a");
        try{
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();recorder.start();
            recording=true;heardVoice=false;
            recordingStarted=SystemClock.elapsedRealtime();
            lastVoice=recordingStarted;
            recordingStatus=text("Recording… Speak your note. Silence will save after 4 seconds.",16,false);
            recordingDialog=new android.app.AlertDialog.Builder(this).setTitle("Voice bookmark")
              .setView(recordingStatus)
              .setPositiveButton("Save note",(d,w)->finishRecording(true))
              .setNegativeButton("Discard",(d,w)->finishRecording(false))
              .setOnCancelListener(d->finishRecording(false))
              .show();
        }catch(Exception ex){
            recording=false;
            if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}
            if(recordingFile!=null)recordingFile.delete();
            resumeAfterCapture();
            new android.app.AlertDialog.Builder(this).setMessage("Microphone unavailable: "+ex.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void monitorRecording(){
        if(recorder==null)return;
        try{
            long now=SystemClock.elapsedRealtime();
            int amplitude=recorder.getMaxAmplitude();
            if(amplitude>1600){heardVoice=true;lastVoice=now;}
            if(recordingStatus!=null)recordingStatus.setText("Recording voice note • "+((now-recordingStarted)/1000)+"s\nSave note, or pause speaking for 4 seconds.");
            if(heardVoice&&now-lastVoice>=4000)finishRecording(true);
        }catch(Exception ignored){}
    }
    private void finishRecording(boolean save){
        if(!recording)return;
        recording=false;
        boolean valid=true;
        try{recorder.stop();}catch(Exception ex){valid=false;}
        try{recorder.release();}catch(Exception ignored){}
        recorder=null;
        if(recordingDialog!=null){recordingDialog.dismiss();recordingDialog=null;}
        if(save&&valid&&recordingFile!=null&&recordingFile.exists()&&recordingFile.length()>0){
            notes.add(new Note(pendingBookUri,"Voice note • tap to listen",pendingPosition,recordingFile.getAbsolutePath()));
            saveNotes();
            if(tts!=null)tts.speak("Note saved",TextToSpeech.QUEUE_FLUSH,null,"note-saved");
        }else if(recordingFile!=null)recordingFile.delete();
        resumeAfterCapture();
        render();
    }
    private void playNoteAudio(String path){
        try{
            MediaPlayer recordingPlayer=new MediaPlayer();
            recordingPlayer.setDataSource(path);
            recordingPlayer.prepare();
            recordingPlayer.setOnCompletionListener(mp->{mp.release();});
            recordingPlayer.start();
        }catch(Exception e){
            new android.app.AlertDialog.Builder(this).setMessage("Unable to play saved note: "+e.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==WAKE_PERMISSION){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startBackgroundHandsFree();
        }
        if(request==MIC_PERMISSION){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)beginRecording();
            else resumeAfterCapture();
        }
    }
    private void dictateNote() {
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak your audiobook note");
        try { startActivityForResult(intent,DICTATE_NOTE); }
        catch(android.content.ActivityNotFoundException ex) {
            new android.app.AlertDialog.Builder(this).setMessage("No speech recognition service is installed. Type your note instead.")
            .setPositiveButton("Type note",(d,w)->typeNote(""))
            .setNegativeButton("Cancel",(d,w)->resumeAfterCapture()).show();
        }
    }
    private void typeNote(String initial) {
        EditText input=new EditText(this);
        input.setSingleLine(false);input.setMinLines(3);
        input.setText(initial);input.setHint("Your note…");
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(dp(20),dp(8),dp(20),0);wrapper.addView(input);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Save note at "+format(pendingPosition))
            .setView(wrapper)
            .setPositiveButton("Save",(d,w)->{
                String value=input.getText().toString().trim();
                if(!value.isEmpty()) {
                    notes.add(new Note(pendingBookUri,value,pendingPosition));
                    saveNotes();
                }
                resumeAfterCapture();
                render();
            })
            .setNegativeButton("Cancel",(d,w)->resumeAfterCapture())
            .setOnCancelListener(d->resumeAfterCapture()).show();
    }
    private void resumeAfterCapture() {
        if(resumeAfterNote && player!=null) {
            if(playback!=null)playback.play();else player.start();
            if(playButton!=null)playButton.setText("❚❚  Pause");
        }
        resumeAfterNote=false;
    }
    private void restoreNotes() {
        try {
            JSONArray a=new JSONArray(prefs.getString("notes","[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject n=a.getJSONObject(i);
                notes.add(new Note(n.getString("bookUri"),n.getString("body"),n.getInt("position"),n.optString("audioPath","")));
            }
        }catch(Exception ignored){}
    }
    private void saveNotes() {
        JSONArray a=new JSONArray();
        for(Note n:notes) {
            JSONObject o=new JSONObject();
            try {
                o.put("bookUri",n.bookUri);o.put("body",n.body);o.put("position",n.position);o.put("audioPath",n.audioPath);
            }catch(Exception ignored){}
            a.put(o);
        }
        prefs.edit().putString("notes",a.toString()).apply();
    }
    private void toggle() {
        if(player==null)return;
        if(playback!=null)playback.toggle();else if(player.isPlaying())player.pause();else player.start();
        playButton.setText(player.isPlaying()?"❚❚  Pause":"▶  Play");saveBooks();
    }
    private void seekTo(int requested) {
        if(player==null)return;
        int position=Math.max(0,Math.min(requested,player.getDuration()));
        if(playback!=null)playback.seek(position);else player.seekTo(position); active.position=position;saveBooks();
    }
    private void openBook(Book book) {
        active=book;
        if(playback==null) {
            new android.app.AlertDialog.Builder(this).setMessage("Player is initializing. Please try again.").setPositiveButton("OK",null).show();
            return;
        }
        try {
            startService(new Intent(this,PlaybackService.class));
            if(playback.player!=null && book.uri.equals(playback.uri)) playback.play();
            else playback.open(book.uri,book.title,book.position);
            player=playback.player;
            render();
            startActivity(new Intent(this,NowPlayingActivity.class));
        }catch(Exception ex){
            new android.app.AlertDialog.Builder(this).setTitle("Unable to open audiobook").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private static String format(int millis) {
        int seconds=Math.max(0,millis/1000);
        return String.format(java.util.Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_AUDIOBOOK_FOLDER){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null){
                Uri folder=data.getData();
                try{
                    getContentResolver().takePersistableUriPermission(folder,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit().putString("audiobookFolder",folder.toString()).apply();
                    scanAudiobookFolder(folder);
                }catch(Exception e){
                    new android.app.AlertDialog.Builder(this).setMessage("Unable to access folder: "+e.getMessage())
                        .setPositiveButton("OK",null).show();
                }
            }
            return;
        }
        if(request==PICK_COVER_FOLDER) {
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&active!=null){
                Uri folder=data.getData();
                try{getContentResolver().takePersistableUriPermission(folder,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                active.folderUri=folder.toString();
                Uri cover=CoverArt.find(this,folder);
                active.coverUri=cover==null?"":cover.toString();
                saveBooks();render();
                if(cover==null)new android.app.AlertDialog.Builder(this).setMessage("No JPG, PNG, or WEBP cover file found in the selected folder. Try selecting the folder containing the audiobook artwork.").setPositiveButton("OK",null).show();
            }
            return;
        }
        if(request==DICTATE_NOTE) {
            if(result==RESULT_OK && data!=null) {
                ArrayList<String> resultText=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                typeNote(resultText!=null&&!resultText.isEmpty()?resultText.get(0):"");
            } else resumeAfterCapture();
            return;
        }
        if(request!=PICK_AUDIO || result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String title="Audiobook";
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){int index=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(index>=0)title=c.getString(index);}
        }catch(Exception ignored){}
        for(Book b:books)if(b.uri.equals(uri.toString())){openBook(b);return;}
        Book b=new Book(uri.toString(),title,0);
        books.add(0,b);saveBooks();openBook(b);
    }
    private void scanAudiobookFolder(Uri folder){
        if(folder==null)return;
        android.app.ProgressDialog progress=new android.app.ProgressDialog(this);
        progress.setMessage("Scanning audiobook folders…");
        progress.setCancelable(false);
        progress.show();
        new Thread(()->{
            java.util.ArrayList<AudiobookFolderScanner.Found> found=null;
            Exception failure=null;
            try{found=AudiobookFolderScanner.scan(getApplicationContext(),folder);}
            catch(Exception e){failure=e;}
            final java.util.ArrayList<AudiobookFolderScanner.Found> results=found;
            final Exception error=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()){progress.dismiss();return;}
                progress.dismiss();
                if(error!=null){
                    new android.app.AlertDialog.Builder(this).setTitle("Folder scan failed")
                        .setMessage(error.getMessage()).setPositiveButton("OK",null).show();return;
                }
                java.util.HashSet<String> existing=new java.util.HashSet<>();
                for(Book b:books)existing.add(b.uri);
                int added=0;
                for(AudiobookFolderScanner.Found item:results){
                    if(existing.add(item.uri)){
                        Book book=new Book(item.uri,item.title,0);
                        book.folderUri=item.folderUri;book.coverUri=item.coverUri;
                        book.album=item.album;book.artist=item.artist;book.track=item.track;
                        books.add(book);added++;
                    }else{
                        for(Book book:books)if(book.uri.equals(item.uri)){
                            if(!item.album.isEmpty())book.album=item.album;
                            if(!item.artist.isEmpty())book.artist=item.artist;
                            if(!item.track.isEmpty())book.track=item.track;
                            if(book.coverUri.isEmpty()&&!item.coverUri.isEmpty())book.coverUri=item.coverUri;
                            break;
                        }
                    }
                }
                saveBooks();render();
                new android.app.AlertDialog.Builder(this).setTitle("Audiobooks scanned")
                    .setMessage("Found "+results.size()+" audio files. Added "+added+" new entries to your library.")
                    .setPositiveButton("OK",null).show();
            });
        }).start();
    }
    private void restoreBooks() {
        try {
            JSONArray items=new JSONArray(prefs.getString("books","[]"));
            for(int i=0;i<items.length();i++){
                JSONObject o=items.getJSONObject(i);
                Book b=new Book(o.getString("uri"),o.getString("title"),o.optInt("position",0));
                b.coverUri=o.optString("coverUri","");b.folderUri=o.optString("folderUri","");
                b.album=o.optString("album","");b.artist=o.optString("artist","");b.track=o.optString("track","");
                books.add(b);
            }
        }catch(Exception ignored){}
    }
    private void saveBooks() {
        JSONArray items=new JSONArray();
        for(Book b:books){JSONObject item=new JSONObject();try{
            item.put("uri",b.uri);item.put("title",b.title);item.put("position",b.position);
            item.put("coverUri",b.coverUri);item.put("folderUri",b.folderUri);
            item.put("album",b.album);item.put("artist",b.artist);item.put("track",b.track);
        }catch(Exception ignored){}items.put(item);}
        prefs.edit().putString("books",items.toString()).apply();
    }
    @Override protected void onResume(){super.onResume();if(buttonTestTicker!=null&&section.equals("tools"))handler.post(buttonTestTicker);if(updater!=null)updater.onResume();if(prefs!=null){notes.clear();restoreNotes();}}
    @Override protected void onPause(){if(buttonTestTicker!=null)handler.removeCallbacks(buttonTestTicker);stopWake();super.onPause();saveBooks();}
    @Override protected void onDestroy(){stopNotePlayer();stopWake();if(recording)finishRecording(false);if(tts!=null)tts.shutdown();if(updater!=null)updater.onDestroy();handler.removeCallbacks(ticker);if(bound)unbindService(serviceConnection);super.onDestroy();}
}
