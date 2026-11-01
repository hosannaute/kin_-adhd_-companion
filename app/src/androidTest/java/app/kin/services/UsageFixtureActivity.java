package app.kin.services;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Runs in the separate test APK. No production code or sensitive system settings. */
public class UsageFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setPadding(24, 24, 24, 24);
        text.setTextSize(20);
        StringBuilder content = new StringBuilder("KIN foreground-time test app\n\n");
        for (int i = 1; i <= 50; i++) content.append("Test content ").append(i).append("\n\n");
        text.setText(content.toString());
        scroll.addView(text);
        setContentView(scroll);
    }
}
