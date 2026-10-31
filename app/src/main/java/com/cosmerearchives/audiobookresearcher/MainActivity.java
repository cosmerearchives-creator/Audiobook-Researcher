package com.cosmerearchives.audiobookresearcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private UpdateManager updates;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 80, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("Audiobook Researcher");
        title.setTextSize(26);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Player and voice bookmarks are coming next. Updates are built in from the first build.");
        subtitle.setPadding(0, 28, 0, 30);
        root.addView(subtitle);
        status = new TextView(this);
        status.setText("Checking release channel…");
        root.addView(status);
        Button check = new Button(this);
        check.setText("Check for updates");
        root.addView(check);
        setContentView(root);
        updates = new UpdateManager(this, status);
        check.setOnClickListener(v -> updates.check());
        updates.check();
    }

    @Override protected void onResume() {
        super.onResume();
        if (updates != null) updates.onResume();
    }
}
