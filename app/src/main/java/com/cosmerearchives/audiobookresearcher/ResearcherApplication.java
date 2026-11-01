package com.cosmerearchives.audiobookresearcher;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Keep a local crash report so a user can report a restart without ADB. */
public final class ResearcherApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                StringWriter writer=new StringWriter();
                error.printStackTrace(new PrintWriter(writer));
                String report=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US)
                    .format(new java.util.Date())+" | "+thread.getName()+"\n"+writer;
                if(report.length()>12000)report=report.substring(0,12000);
                getSharedPreferences("library",MODE_PRIVATE).edit().putString("lastCrashReport",report).commit();
                Log.e("AudiobookResearcher","Unhandled crash",error);
            }catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
            else Process.killProcess(Process.myPid());
        });
    }
}
