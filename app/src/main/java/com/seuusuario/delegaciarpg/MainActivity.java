package com.seuusuario.delegaciarpg;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (gameView != null) {
            gameView.pause();
        }
        super.onPause();
    }
}