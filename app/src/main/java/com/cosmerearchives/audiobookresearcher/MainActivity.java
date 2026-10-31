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
import java.util.ArrayList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 200;
    private static final int DICTATE_NOTE = 201;
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
    private Book active;
    private TextView status, progress, playButton, currentBook;
    private ProgressBar updateProgress;
    private TextView updateDetails, installUpdate;
    private SeekBar seek;
    private boolean tracking;
    private UpdateManager updater;

    private static class Book {
        String uri, title; int position;
        Book(String u, String t, int p) { uri=u; title=t; position=p; }
    }

    private static class Note {
        String bookUri, body; int position;
        Note(String u, String b, int p) { bookUri=u; body=b; position=p; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("library", MODE_PRIVATE);
        restoreBooks();
        restoreNotes();
        render();
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(28),dp(20),dp(30)); scroll.addView(root);
        TextView heading = text("Audiobook Researcher", 28, true); root.addView(heading);
        space(root,7);
        TextView sub = text("Your listening library",15,false);
        sub.setTextColor(0xFF807D77); root.addView(sub);
        space(root,24);
        TextView importButton = button("+ Import audiobook",true);
        root.addView(importButton);
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,PICK_AUDIO);
        });
        space(root,22);
        TextView libraryTitle = text("Your books",21,true); root.addView(libraryTitle);
        space(root,12);
        if (books.isEmpty()) {
            TextView empty = text("No audiobooks yet. Import an MP3, M4B, or another audio file to start listening.",15,false);
            empty.setPadding(dp(16),dp(22),dp(16),dp(22));
            empty.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            root.addView(empty);
        }
        for (Book book : books) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16),dp(14),dp(16),dp(14)); row.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            TextView title = text(book.title,17,true); row.addView(title);
            space(row,5);
            TextView detail = text("Resume at " + format(book.position),13,false);
            detail.setTextColor(0xFF817E78); row.addView(detail);
            root.addView(row); space(root,10);
            row.setOnClickListener(v -> openBook(book));
        }
        space(root,14);
        if (active != null && player != null) {
            LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16),dp(18),dp(16),dp(18));
            panel.setBackground(shape(Color.WHITE,16,0xFFE0DDD8));
            currentBook=text("NOW PLAYING  •  " + active.title,14,true); panel.addView(currentBook);
            space(panel,12);
            seek = new SeekBar(this); seek.setMax(Math.max(1,player.getDuration())); seek.setProgress(active.position);
            seek.setProgressTintList(ColorStateList.valueOf(PURPLE));
            panel.addView(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onStartTrackingTouch(SeekBar bar){tracking=true;}
                public void onProgressChanged(SeekBar bar,int p,boolean fromUser){}
                public void onStopTrackingTouch(SeekBar bar){tracking=false;seekTo(bar.getProgress());}
            });
            progress=text(format(active.position)+" / "+format(player.getDuration()),13,false);
            progress.setGravity(Gravity.CENTER); panel.addView(progress);
            space(panel,15);
            LinearLayout buttons = new LinearLayout(this); buttons.setGravity(Gravity.CENTER); buttons.setOrientation(LinearLayout.HORIZONTAL);
            TextView back=button("−15s",false), forward=button("+30s",false);
            playButton=button(player.isPlaying()?"Pause":"Play",true);
            for(TextView control:new TextView[]{back,playButton,forward}) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(48),1);
                p.setMargins(dp(3),0,dp(3),0); buttons.addView(control,p);
            }
            back.setOnClickListener(v->seekTo(player.getCurrentPosition()-15000));
            forward.setOnClickListener(v->seekTo(player.getCurrentPosition()+30000));
            playButton.setOnClickListener(v->toggle());
            panel.addView(buttons); root.addView(panel);
            space(root,14);
            TextView noteButton=button("+ Add bookmarked note",true);
            root.addView(noteButton);
            noteButton.setOnClickListener(v -> startNote());
            space(root,18);
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
        setContentView(scroll);
    }
    private void startNote() {
        if(active==null||player==null)return;
        pendingBookUri=active.uri;
        pendingPosition=player.getCurrentPosition();
        resumeAfterNote=player.isPlaying();
        if(resumeAfterNote)player.pause();
        if(playButton!=null)playButton.setText("Play");
        String[] choices={"Dictate note","Type note","Cancel"};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Bookmark at "+format(pendingPosition))
            .setItems(choices,(dialog,which)->{
                if(which==0)dictateNote();
                else if(which==1)typeNote("");
                else resumeAfterCapture();
            }).setOnCancelListener(d -> resumeAfterCapture()).show();
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
            player.start();
            if(playButton!=null)playButton.setText("Pause");
        }
        resumeAfterNote=false;
    }
    private void restoreNotes() {
        try {
            JSONArray a=new JSONArray(prefs.getString("notes","[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject n=a.getJSONObject(i);
                notes.add(new Note(n.getString("bookUri"),n.getString("body"),n.getInt("position")));
            }
        }catch(Exception ignored){}
    }
    private void saveNotes() {
        JSONArray a=new JSONArray();
        for(Note n:notes) {
            JSONObject o=new JSONObject();
            try {
                o.put("bookUri",n.bookUri);o.put("body",n.body);o.put("position",n.position);
            }catch(Exception ignored){}
            a.put(o);
        }
        prefs.edit().putString("notes",a.toString()).apply();
    }
    private void toggle() {
        if(player==null)return;
        if(player.isPlaying())player.pause();else player.start();
        playButton.setText(player.isPlaying()?"Pause":"Play");saveBooks();
    }
    private void seekTo(int requested) {
        if(player==null)return;
        int position=Math.max(0,Math.min(requested,player.getDuration()));
        player.seekTo(position); active.position=position;saveBooks();
    }
    private void openBook(Book book) {
        if(player!=null){player.release();player=null;}
        active=book;
        try {
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(this,Uri.parse(book.uri));
            player.prepare();
            player.seekTo(Math.min(book.position,player.getDuration()));
            player.setOnCompletionListener(mp->{book.position=0;saveBooks(); if(playButton!=null)playButton.setText("Play");});
            player.start();
            render();
        } catch(Exception ex) {
            if(player!=null){player.release();player=null;}
            active=null;
            new android.app.AlertDialog.Builder(this).setTitle("Unable to open audiobook").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private static String format(int millis) {
        int seconds=Math.max(0,millis/1000);
        return String.format(java.util.Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
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
    private void restoreBooks() {
        try {
            JSONArray items=new JSONArray(prefs.getString("books","[]"));
            for(int i=0;i<items.length();i++){
                JSONObject o=items.getJSONObject(i);
                books.add(new Book(o.getString("uri"),o.getString("title"),o.optInt("position",0)));
            }
        }catch(Exception ignored){}
    }
    private void saveBooks() {
        JSONArray items=new JSONArray();
        for(Book b:books){JSONObject item=new JSONObject();try{
            item.put("uri",b.uri);item.put("title",b.title);item.put("position",b.position);
        }catch(Exception ignored){}items.put(item);}
        prefs.edit().putString("books",items.toString()).apply();
    }
    @Override protected void onResume(){super.onResume();if(updater!=null)updater.onResume();}
    @Override protected void onPause(){super.onPause();saveBooks();}
    @Override protected void onDestroy(){if(updater!=null)updater.onDestroy();handler.removeCallbacks(ticker);if(player!=null)player.release();super.onDestroy();}
}
