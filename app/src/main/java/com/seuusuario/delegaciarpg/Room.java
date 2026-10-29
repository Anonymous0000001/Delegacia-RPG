package com.seuusuario.delegaciarpg;

public class Room {
    private final String name;
    private volatile boolean isUnlocked;
    private final int costSucata;
    private final int costMoral;
    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public Room(String name, int costSucata, int costMoral, float x, float y,
                float width, float height) {
        this.name = name;
        this.costSucata = costSucata;
        this.costMoral = costMoral;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        isUnlocked = false;
    }

    public boolean tentarReformar(int playerSucata, int playerMoral) {
        if (!isUnlocked && playerSucata >= costSucata && playerMoral >= costMoral) {
            isUnlocked = true;
            return true;
        }
        return false;
    }

    public String getName() {
        return name;
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public int getCostSucata() {
        return costSucata;
    }

    public int getCostMoral() {
        return costMoral;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}