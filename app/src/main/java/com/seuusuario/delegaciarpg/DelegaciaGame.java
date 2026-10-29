package com.seuusuario.delegaciarpg;

import com.badlogic.gdx.Game;

public class DelegaciaGame extends Game {
    @Override
    public void create() {
        setScreen(new GameScreen());
    }
}