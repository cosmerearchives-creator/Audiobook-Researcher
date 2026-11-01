package com.cosmerearchives.audiobookresearcher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.Locale;

/** Search a user-granted audiobook directory for nearby cover artwork. */
final class CoverArt {
    private CoverArt(){}
    static Uri find(Context context, Uri tree) {
        if(tree==null)return null;
        try {
            String id=DocumentsContract.getTreeDocumentId(tree);
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
            Uri best=null;int bestScore=-1;
            try(Cursor c=context.getContentResolver().query(children,new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                if(c==null)return null;
                while(c.moveToNext()){
                    String name=c.getString(1);
                    String mime=c.getString(2);
                    if(name==null||mime==null||!mime.startsWith("image/"))continue;
                    String lower=name.toLowerCase(Locale.ROOT);
                    if(!(lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp")))continue;
                    int score=1;
                    if(lower.startsWith("cover."))score=10;
                    else if(lower.startsWith("folder."))score=9;
                    else if(lower.startsWith("front."))score=8;
                    else if(lower.contains("cover"))score=7;
                    else if(lower.contains("artwork"))score=6;
                    if(score>bestScore){
                        best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));
                        bestScore=score;
                    }
                }
            }
            return best;
        }catch(Exception ignored){return null;}
    }
}
