package com.cosmerearchives.audiobookresearcher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Recursively discovers audiobook audio documents in a user-granted SAF tree. */
final class AudiobookFolderScanner {
    static final class Found {
        final String uri,title,folderUri,coverUri;
        Found(String u,String t,String f,String c){uri=u;title=t;folderUri=f;coverUri=c;}
    }
    static ArrayList<Found> scan(Context context, Uri tree) {
        ArrayList<Found> found=new ArrayList<>();
        ArrayDeque<String> directories=new ArrayDeque<>();
        HashSet<String> visited=new HashSet<>();
        directories.add(DocumentsContract.getTreeDocumentId(tree));
        while(!directories.isEmpty()) {
            String id=directories.removeFirst();
            if(!visited.add(id))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
            ArrayList<String[]> audio=new ArrayList<>();
            ArrayList<String[]> images=new ArrayList<>();
            try(Cursor c=context.getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)) {
                if(c==null)continue;
                while(c.moveToNext()) {
                    String docId=c.getString(0),name=c.getString(1),mime=c.getString(2);
                    if(docId==null||name==null)continue;
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){directories.addLast(docId);continue;}
                    String lower=name.toLowerCase(Locale.ROOT);
                    if(lower.endsWith(".mp3")||lower.endsWith(".m4b")||lower.endsWith(".m4a")
                        ||lower.endsWith(".aac")||lower.endsWith(".flac")||lower.endsWith(".ogg")
                        ||lower.endsWith(".opus")||lower.endsWith(".wav")||lower.endsWith(".wma"))
                        audio.add(new String[]{docId,name});
                    else if(lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp"))
                        images.add(new String[]{docId,name});
                }
            }catch(Exception ignored){continue;}
            String cover="";
            int best=-1;
            for(String[] img:images){
                String n=img[1].toLowerCase(Locale.ROOT);
                int score=n.startsWith("cover.")?5:n.startsWith("folder.")?4:n.startsWith("front.")?3:n.contains("cover")?2:1;
                if(score>best){
                    best=score;
                    cover=DocumentsContract.buildDocumentUriUsingTree(tree,img[0]).toString();
                }
            }
            String directoryUri=DocumentsContract.buildDocumentUriUsingTree(tree,id).toString();
            for(String[] file:audio){
                String uri=DocumentsContract.buildDocumentUriUsingTree(tree,file[0]).toString();
                found.add(new Found(uri,file[1],directoryUri,cover));
            }
        }
        return found;
    }
}
