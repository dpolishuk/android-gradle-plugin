package org.gradle.sample;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;

import java.lang.String;
import java.util.Arrays;

public class ShowPeopleActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String message = "People:";

        Iterable<Person> people = new People();
        for (Person person : people) {
            message += "\n * ";
            message += person.getName();
        }

        TextView textView = new TextView(this);
        textView.setTextSize(20);
        textView.setText(message);

        setContentView(textView);
    }
}
