package com.seuusuario.delegaciarpg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    private final SurfaceHolder surfaceHolder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Room> delegaciaRooms = new ArrayList<>();
    private final Random random = new Random();
    private final NPCInteractionManager npcInteractionManager = new NPCInteractionManager();
    private final RectF quadroEspingardaRect = new RectF();
    private final float density;
    private Button btnUp;
    private Button btnDown;
    private Button btnLeft;
    private Button btnRight;
    private Button btnPatrulha;
    private Button btnInvestigar;
    private Button btnProximaFase;
    private Button btnHackear;
    private int controlsWidth = -1;
    private int controlsHeight = -1;

    private volatile boolean isPlaying;
    private volatile boolean surfaceReady;
    private volatile boolean movingUp;
    private volatile boolean movingDown;
    private volatile boolean movingLeft;
    private volatile boolean movingRight;
    private volatile boolean jogadorPertoDoQuadro;
    private Thread gameThread;

    private volatile float playerX;
    private volatile float playerY;
    private float playerFacingDegrees;
    private float animationSeconds;
    private boolean playerMoving;
    private volatile int dinheiro = 1500;
    private volatile int sucata = 50;
    private volatile int moral = 30;
    private volatile int faseAtual = 1;
    private volatile String nomeFase = "Fase 1: Centro Abandonado";
    private volatile int celasOcupadas;
    private volatile int capacidadeCelas = 4;
    private volatile int viaturasAtivas = 1;
    private volatile int detetivesContratados;
    private volatile String statusMessage = "Use o D-pad para andar";
    private volatile String npcDialogueSpeaker = "COMUNICACAO DA DELEGACIA";
    private volatile String npcDialogue = "";
    private volatile float dialogueSecondsRemaining;
    private float dayProgressSeconds;

    public GameView(Context context) {
        super(context);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);

        delegaciaRooms.add(new Room("Recepção", 30, 10, 0.08f, 0.02f, 0.84f, 0.27f));
        delegaciaRooms.add(new Room("Arsenal", 60, 25, 0.08f, 0.365f, 0.84f, 0.27f));
        delegaciaRooms.add(new Room("Cela de Detenção", 100, 40, 0.08f, 0.71f, 0.84f, 0.27f));
    }

    @Override
    public void run() {
        long previousFrame = System.nanoTime();
        while (isPlaying) {
            long frameStart = System.nanoTime();
            float deltaSeconds = Math.min((frameStart - previousFrame) / 1_000_000_000f, 0.05f);
            previousFrame = frameStart;
            update(deltaSeconds);
            drawFrame();

            long frameMillis = (System.nanoTime() - frameStart) / 1_000_000L;
            if (frameMillis < 16) {
                try {
                    Thread.sleep(16 - frameMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void update(float deltaSeconds) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        ensureControls(width, height);
        animationSeconds += deltaSeconds;
        if (dialogueSecondsRemaining > 0) {
            dialogueSecondsRemaining = Math.max(0, dialogueSecondsRemaining - deltaSeconds);
            if (dialogueSecondsRemaining == 0) {
                npcDialogue = "";
            }
        }

        dayProgressSeconds += deltaSeconds;
        if (dayProgressSeconds >= 60f) {
            int daysPassed = (int) (dayProgressSeconds / 60f);
            dayProgressSeconds -= daysPassed * 60f;
            int dailyIncome = 0;
            if (isRoomUnlocked("Cela de Detenção")) {
                dailyIncome += celasOcupadas * 100;
            }
            if (isRoomUnlocked("Arsenal")) {
                dailyIncome += detetivesContratados * 150;
            }
            if (dailyIncome > 0) {
                dinheiro += dailyIncome * daysPassed;
                statusMessage = "Renda diaria: +R$ " + dailyIncome * daysPassed;
            }
        }

        if (playerX == 0 && playerY == 0) {
            playerX = width * 0.5f;
            playerY = roomBoard(width, height).centerY();
        }

        float distanceToFrame = (float) Math.hypot(
            playerX - quadroEspingardaRect.centerX(),
            playerY - quadroEspingardaRect.centerY());
        jogadorPertoDoQuadro = distanceToFrame < 145f * density;

        float directionX = (movingRight ? 1f : 0f) - (movingLeft ? 1f : 0f);
        float directionY = (movingDown ? 1f : 0f) - (movingUp ? 1f : 0f);
        float directionLength = (float) Math.hypot(directionX, directionY);
        if (directionLength == 0) {
            playerMoving = false;
            return;
        }

        playerMoving = true;
        playerFacingDegrees = (float) Math.toDegrees(Math.atan2(directionY, directionX)) + 90f;
        float speed = 220f * density * deltaSeconds;
        float radius = 16f * density;
        RectF board = roomBoard(width, height);
        playerX = clamp(playerX + directionX / directionLength * speed,
            board.left + radius, board.right - radius);
        playerY = clamp(playerY + directionY / directionLength * speed,
            board.top + radius, board.bottom - radius);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private void drawFrame() {
        if (!surfaceHolder.getSurface().isValid()) {
            return;
        }

        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            drawGame(canvas);
        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawGame(Canvas canvas) {
        float width = canvas.getWidth();
        float height = canvas.getHeight();
        ensureControls(width, height);
        canvas.drawColor(Color.rgb(25, 31, 35));
        drawFloorTexture(canvas, width, height);
        drawHeader(canvas, width);
        drawRooms(canvas, width, height);
        drawWeaponFrame(canvas);
        drawPatrolVehicle(canvas, width, height);
        drawPlayer(canvas);
        drawControlDock(canvas, width, height);
        drawControls(canvas);
        drawDialogue(canvas, width, height);
    }

    private void drawHeader(Canvas canvas, float width) {
        boolean landscape = isLandscape(width, getHeight());
        float headerHeight = headerHeight(width, getHeight());
        setColor(Color.rgb(17, 21, 24));
        canvas.drawRect(0, 0, width, headerHeight, paint);
        setStrokeColor(Color.rgb(72, 81, 84));
        paint.setStrokeWidth(1f * density);
        canvas.drawLine(0, headerHeight, width, headerHeight, paint);

        if (landscape) {
            drawText(canvas, "DELEGACIA RPG", 24f * density, 24f * density, 16f,
                Color.WHITE, Paint.Align.LEFT, true);
            drawText(canvas, "R$ " + dinheiro, 24f * density, 49f * density, 11f,
                Color.rgb(208, 235, 119), Paint.Align.LEFT, true);
            drawText(canvas, "SUCATA " + sucata, width * 0.5f, 49f * density, 11f,
                Color.rgb(238, 191, 111), Paint.Align.CENTER, true);
            drawText(canvas, "MORAL " + moral, width - 24f * density, 49f * density, 11f,
                Color.rgb(174, 187, 184), Paint.Align.RIGHT, true);
            drawText(canvas, nomeFase, 24f * density, 75f * density, 9f,
                Color.rgb(205, 215, 205), Paint.Align.LEFT, true);
            drawText(canvas, "CELAS " + celasOcupadas + "/" + capacidadeCelas
                    + " | VTR " + viaturasAtivas + " | DET " + detetivesContratados,
                width - 24f * density, 75f * density, 8f,
                Color.rgb(205, 215, 205), Paint.Align.RIGHT, true);
            drawText(canvas, statusMessage, width * 0.5f, 88f * density, 8f,
                Color.rgb(174, 187, 184), Paint.Align.CENTER, false);
        } else {
            drawText(canvas, "DELEGACIA RPG", 24f * density, 42f * density, 23f,
                Color.WHITE, Paint.Align.LEFT, true);
            drawText(canvas, "Reconstrua a delegacia", 24f * density, 66f * density, 13f,
                Color.rgb(174, 187, 184), Paint.Align.LEFT, false);
            drawText(canvas, "R$ " + dinheiro, 24f * density, 105f * density, 13f,
                Color.rgb(208, 235, 119), Paint.Align.LEFT, true);
            drawText(canvas, "SUCATA " + sucata, width * 0.5f, 105f * density, 12f,
                Color.rgb(238, 191, 111), Paint.Align.CENTER, true);
            drawText(canvas, "MORAL " + moral, width - 24f * density, 105f * density, 12f,
                Color.rgb(174, 187, 184), Paint.Align.RIGHT, true);
            drawText(canvas, nomeFase, 24f * density, 134f * density, 10f,
                Color.rgb(205, 215, 205), Paint.Align.LEFT, true);
            drawText(canvas, "CELAS " + celasOcupadas + "/" + capacidadeCelas
                    + " | VTR " + viaturasAtivas + " | DET " + detetivesContratados,
                width - 24f * density, 134f * density, 9f,
                Color.rgb(205, 215, 205), Paint.Align.RIGHT, true);
            drawText(canvas, statusMessage, width * 0.5f, 158f * density, 9f,
                Color.rgb(174, 187, 184), Paint.Align.CENTER, false);
        }
    }

    private void drawRooms(Canvas canvas, float width, float height) {
        RectF board = roomBoard(width, height);
        for (Room room : delegaciaRooms) {
            RectF bounds = roomBounds(room, width, board);
            boolean unlocked = room.isUnlocked();
            setColor(unlocked ? Color.rgb(46, 105, 70) : Color.rgb(112, 47, 45));
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, paint);
            drawRoomTexture(canvas, bounds, room, unlocked);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            setStrokeColor(unlocked ? Color.rgb(111, 190, 124) : Color.rgb(170, 91, 77));
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, paint);
            paint.setStyle(Paint.Style.FILL);

                boolean landscape = isLandscape(width, height);
                float left = bounds.left + (landscape ? 8f : 16f) * density;
                float titleSize = landscape ? 11f : 16f;
                String title = landscape ? compactRoomName(room) : room.getName();
                drawText(canvas, title, left, bounds.top + (landscape ? 18f : 25f) * density,
                    titleSize, Color.WHITE, Paint.Align.LEFT, true);
            drawText(canvas, unlocked ? "REFORMADO" : "EM RUÍNAS", left,
                    bounds.top + (landscape ? 33f : 45f) * density, landscape ? 8f : 10f,
                    unlocked ? Color.rgb(187, 235, 174) : Color.rgb(250, 191, 160),
                    Paint.Align.LEFT, true);
            if (unlocked) {
                String roomStatus;
                if (room.getName().contains("Cela")) {
                    roomStatus = "Presos: " + celasOcupadas + " / " + capacidadeCelas;
                } else if (room.getName().contains("Arsenal")) {
                    roomStatus = "Detetives: " + detetivesContratados;
                } else {
                    roomStatus = "Base operacional";
                }
                drawText(canvas, roomStatus, left, bounds.bottom - 9f * density,
                    landscape ? 7f : 11f, Color.rgb(223, 236, 218), Paint.Align.LEFT, false);
            } else {
                String costs = landscape
                    ? room.getCostSucata() + " suc / " + room.getCostMoral() + " moral"
                    : "Custo: " + room.getCostSucata() + " sucata  |  "
                        + room.getCostMoral() + " moral";
                drawText(canvas, costs, left, bounds.bottom - 9f * density,
                    landscape ? 7f : 12f,
                        Color.rgb(245, 220, 194), Paint.Align.LEFT, false);
            }
        }
    }

    private void drawFloorTexture(Canvas canvas, float width, float height) {
        RectF board = roomBoard(width, height);
        setColor(Color.rgb(34, 39, 43));
        canvas.drawRect(board, paint);
        canvas.save();
        canvas.clipRect(board);

        float tileSize = 42f * density;
        int row = 0;
        for (float y = board.top; y < board.bottom; y += tileSize) {
            int column = 0;
            for (float x = board.left; x < board.right; x += tileSize) {
                int shade = (row + column) % 2 == 0 ? 39 : 43;
                setColor(Color.rgb(shade, shade + 5, shade + 7));
                canvas.drawRect(x, y, x + tileSize, y + tileSize, paint);
                column++;
            }
            row++;
        }

        setStrokeColor(Color.argb(75, 12, 16, 20));
        paint.setStrokeWidth(1f * density);
        for (float x = board.left; x <= board.right; x += tileSize) {
            canvas.drawLine(x, board.top, x, board.bottom, paint);
        }
        for (float y = board.top; y <= board.bottom; y += tileSize) {
            canvas.drawLine(board.left, y, board.right, y, paint);
        }

        for (int index = 0; index < 7; index++) {
            float stainX = board.left + ((index * 137f + 53f) % Math.max(1f, width - 36f * density));
            float stainY = board.top + ((index * 83f + 29f) % Math.max(1f, board.height()));
            setColor(Color.argb(24, 12, 18, 19));
            canvas.drawOval(stainX, stainY, stainX + 16f * density,
                    stainY + 7f * density, paint);
        }

        for (int index = 0; index < 6; index++) {
            float drift = (animationSeconds * (8f + index * 1.5f) + index * 61f) % Math.max(1f, board.width());
            float moteY = board.top + ((index * 97f + 41f) % Math.max(1f, board.height()));
            setColor(Color.argb(50, 196, 200, 176));
            canvas.drawCircle(board.left + drift, moteY, (1f + index % 2) * density, paint);
        }
        canvas.restore();
    }

    private void drawRoomTexture(Canvas canvas, RectF bounds, Room room, boolean unlocked) {
        canvas.save();
        canvas.clipRect(bounds);
        int baseShade = unlocked ? 73 : 63;
        setColor(Color.argb(40, baseShade + 15, baseShade + 18, baseShade + 15));
        float tileSize = 22f * density;
        for (float x = bounds.left; x < bounds.right; x += tileSize) {
            for (float y = bounds.top; y < bounds.bottom; y += tileSize) {
                if ((((int) ((x - bounds.left) / tileSize))
                        + ((int) ((y - bounds.top) / tileSize))) % 2 == 0) {
                    canvas.drawRect(x, y, x + tileSize, y + tileSize, paint);
                }
            }
        }

        if (room.getName().contains("Cela")) {
            paint.setStrokeWidth(2f * density);
            setStrokeColor(Color.argb(unlocked ? 105 : 135, 22, 25, 28));
            float barSpacing = 18f * density;
            for (float x = bounds.left + 12f * density; x < bounds.right; x += barSpacing) {
                canvas.drawLine(x, bounds.top, x, bounds.bottom, paint);
            }
            setColor(Color.argb(100, 145, 153, 143));
            canvas.drawRoundRect(bounds.left + 18f * density, bounds.top + 17f * density,
                    bounds.left + 60f * density, bounds.top + 31f * density,
                    4f * density, 4f * density, paint);
        } else if (room.getName().contains("Arsenal")) {
            setColor(Color.argb(105, 27, 35, 40));
            canvas.drawRect(bounds.left + 15f * density, bounds.top + 17f * density,
                    bounds.right - 15f * density, bounds.top + 26f * density, paint);
            paint.setStrokeWidth(1.5f * density);
            setStrokeColor(Color.argb(135, 205, 176, 113));
            for (int index = 0; index < 4; index++) {
                float rackX = bounds.left + (34f + index * 20f) * density;
                canvas.drawLine(rackX, bounds.top + 7f * density,
                        rackX + 5f * density, bounds.top + 17f * density, paint);
            }
        } else {
            setColor(Color.argb(115, 38, 42, 42));
            canvas.drawRoundRect(bounds.centerX() - 34f * density,
                    bounds.centerY() - 11f * density, bounds.centerX() + 34f * density,
                    bounds.centerY() + 8f * density, 5f * density, 5f * density, paint);
            setColor(Color.argb(120, 115, 104, 78));
            canvas.drawRect(bounds.centerX() - 30f * density, bounds.centerY() - 15f * density,
                    bounds.centerX() + 30f * density, bounds.centerY() - 9f * density, paint);
        }
        canvas.restore();
    }

    private void drawPlayer(Canvas canvas) {
        float step = playerMoving ? (float) Math.sin(animationSeconds * 13f) : 0f;
        float idleBreath = playerMoving ? 0f : (float) Math.sin(animationSeconds * 2.4f) * 0.8f;
        float bob = Math.abs(step) * 1.6f * density + idleBreath * density;
        float legSwing = step * 4f * density;

        setColor(Color.argb(90, 8, 12, 14));
        canvas.drawOval(playerX - 10f * density, playerY + 10f * density,
            playerX + 10f * density, playerY + 15f * density, paint);

        canvas.save();
        canvas.rotate(playerFacingDegrees, playerX, playerY);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3.2f * density);
        setStrokeColor(Color.rgb(18, 27, 42));
        canvas.drawLine(playerX - 3f * density, playerY + 4f * density + bob,
            playerX - 4f * density - legSwing, playerY + 13f * density, paint);
        canvas.drawLine(playerX + 3f * density, playerY + 4f * density + bob,
            playerX + 4f * density + legSwing, playerY + 13f * density, paint);

        setColor(Color.rgb(29, 66, 104));
        canvas.drawRoundRect(playerX - 6.5f * density, playerY - 5f * density + bob,
            playerX + 6.5f * density, playerY + 7f * density + bob,
            3f * density, 3f * density, paint);
        paint.setStrokeWidth(2.4f * density);
        setStrokeColor(Color.rgb(25, 55, 87));
        canvas.drawLine(playerX - 5f * density, playerY - 2f * density + bob,
            playerX - 8f * density - legSwing * 0.35f, playerY + 5f * density + bob, paint);
        canvas.drawLine(playerX + 5f * density, playerY - 2f * density + bob,
            playerX + 8f * density + legSwing * 0.35f, playerY + 5f * density + bob, paint);

        setColor(Color.rgb(220, 180, 139));
        canvas.drawCircle(playerX, playerY - 10f * density + bob, 4.7f * density, paint);
        setColor(Color.rgb(18, 29, 49));
        canvas.drawRoundRect(playerX - 5f * density, playerY - 15f * density + bob,
            playerX + 5f * density, playerY - 12f * density + bob,
            2f * density, 2f * density, paint);
        setColor(Color.rgb(241, 195, 72));
        canvas.drawCircle(playerX + 3.2f * density, playerY - 1f * density + bob,
            1.4f * density, paint);
        canvas.restore();

        drawText(canvas, "POLICIAL", playerX, playerY - 20f * density - bob, 8f,
                Color.WHITE, Paint.Align.CENTER, true);
    }

        private void drawPatrolVehicle(Canvas canvas, float width, float height) {
        RectF board = roomBoard(width, height);
        float centerX = width * 0.5f;
            float centerY = isLandscape(width, height)
                ? board.bottom - 12f * density
                : board.top + board.height() * 0.327f;
        float carWidth = 34f * density;
        float carHeight = 17f * density;
        RectF body = new RectF(centerX - carWidth * 0.5f, centerY - carHeight * 0.5f,
            centerX + carWidth * 0.5f, centerY + carHeight * 0.5f);
        setColor(Color.argb(100, 8, 11, 13));
        canvas.drawOval(body.left + 2f * density, body.bottom - 1f * density,
            body.right + 2f * density, body.bottom + 4f * density, paint);
        setColor(Color.rgb(212, 219, 218));
        canvas.drawRoundRect(body, 5f * density, 5f * density, paint);
        setColor(Color.rgb(35, 53, 69));
        canvas.drawRoundRect(body.left + 9f * density, body.top + 2f * density,
            body.right - 8f * density, body.bottom - 2f * density,
            3f * density, 3f * density, paint);
        setColor(Color.rgb(21, 25, 29));
        canvas.drawCircle(body.left + 8f * density, body.bottom - 1f * density,
            3f * density, paint);
        canvas.drawCircle(body.right - 8f * density, body.bottom - 1f * density,
            3f * density, paint);

        float pulse = (float) Math.sin(animationSeconds * 8f);
        int redAlpha = 115 + (int) (Math.max(0f, pulse) * 140f);
        int blueAlpha = 115 + (int) (Math.max(0f, -pulse) * 140f);
        setColor(Color.argb(redAlpha, 255, 45, 50));
        canvas.drawRoundRect(centerX - 5f * density, body.top - 3f * density,
            centerX, body.top + 1f * density, density, density, paint);
        setColor(Color.argb(blueAlpha, 45, 130, 255));
        canvas.drawRoundRect(centerX, body.top - 3f * density,
            centerX + 5f * density, body.top + 1f * density, density, density, paint);
        drawText(canvas, "VTR", centerX, body.bottom + 11f * density, 7f,
            Color.rgb(223, 229, 220), Paint.Align.CENTER, true);
        }

    private void drawWeaponFrame(Canvas canvas) {
        setColor(Color.rgb(30, 35, 45));
        canvas.drawRoundRect(quadroEspingardaRect, 6f * density, 6f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        setStrokeColor(jogadorPertoDoQuadro
                ? Color.rgb(0, 210, 115) : Color.rgb(100, 115, 135));
        canvas.drawRoundRect(quadroEspingardaRect, 6f * density, 6f * density, paint);
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, "CAL. 12", quadroEspingardaRect.centerX(),
                quadroEspingardaRect.centerY() + 3f * density, 8f,
                Color.WHITE, Paint.Align.CENTER, true);
    }

    private void drawDialogue(Canvas canvas, float width, float height) {
        String message = npcDialogue;
        if (message.isEmpty()) {
            return;
        }

        float textSize = 12f * density;
        float maxTextWidth = width - 48f * density;
        paint.setTextSize(textSize);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        List<String> lines = wrapDialogue(message, maxTextWidth, 3);
        float lineHeight = 16f * density;
        float padding = 10f * density;
        float speakerHeight = 14f * density;
        float boxHeight = lines.size() * lineHeight + speakerHeight + padding * 2f;
        float boxWidth = width - 32f * density;
        float boxX = (width - boxWidth) * 0.5f;
        RectF board = roomBoard(width, height);
        float boxY;
        if (isLandscape(width, height)) {
            boxY = clamp(board.centerY() - boxHeight * 0.5f,
                    board.top + 2f * density, board.bottom - boxHeight - 2f * density);
        } else {
            boxY = playerY - 30f * density - boxHeight;
            float minY = headerHeight(width, height) + 4f * density;
            float maxY = Math.max(minY, board.bottom - boxHeight);
            if (boxY < minY || boxY > maxY) {
                boxY = Math.max(minY, Math.min(playerY + 34f * density, maxY));
            }
        }

        RectF bubble = new RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight);
        setColor(Color.argb(225, 15, 20, 25));
        canvas.drawRoundRect(bubble, 8f * density, 8f * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        setStrokeColor(Color.argb(180, 174, 187, 184));
        canvas.drawRoundRect(bubble, 8f * density, 8f * density, paint);

        drawText(canvas, npcDialogueSpeaker, boxX + padding, boxY + padding + 9f * density,
            9f, Color.rgb(208, 235, 119), Paint.Align.LEFT, true);
        for (int index = 0; index < lines.size(); index++) {
            drawText(canvas, lines.get(index), width * 0.5f,
                boxY + padding + speakerHeight + (index + 1) * lineHeight - 3f * density,
                    12f, Color.WHITE, Paint.Align.CENTER, false);
        }
    }

    private List<String> wrapDialogue(String message, float maxWidth, int maxLines) {
        paint.setTextSize(12f * density);
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        String[] words = message.split("\\s+");
        boolean truncated = false;

        for (int index = 0; index < words.length; index++) {
            String candidate = currentLine.length() == 0
                    ? words[index] : currentLine + " " + words[index];
            if (paint.measureText(candidate) <= maxWidth || currentLine.length() == 0) {
                currentLine.setLength(0);
                currentLine.append(candidate);
            } else {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(words[index]);
                if (lines.size() == maxLines - 1 && index < words.length - 1) {
                    truncated = true;
                    break;
                }
            }
        }

        if (currentLine.length() > 0 && lines.size() < maxLines) {
            lines.add(currentLine.toString());
        }
        if (truncated && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            String lastLine = lines.get(lastIndex);
            while (!lastLine.isEmpty() && paint.measureText(lastLine + "...") > maxWidth) {
                lastLine = lastLine.substring(0, lastLine.length() - 1);
            }
            lines.set(lastIndex, lastLine.trim() + "...");
        }
        return lines;
    }

    private void showNpcDialogue(String speaker, String message, String status) {
        npcDialogueSpeaker = speaker;
        npcDialogue = message;
        dialogueSecondsRemaining = 6f;
        statusMessage = status;
    }

    private void drawControls(Canvas canvas) {
        btnUp.draw(canvas, paint);
        btnDown.draw(canvas, paint);
        btnLeft.draw(canvas, paint);
        btnRight.draw(canvas, paint);
        btnPatrulha.draw(canvas, paint);
        btnInvestigar.draw(canvas, paint);
        btnProximaFase.draw(canvas, paint);
        if (jogadorPertoDoQuadro) {
            btnHackear.draw(canvas, paint);
        }
    }

    private void drawControlDock(Canvas canvas, float width, float height) {
        float top = height - controlsPanelHeight(width, height);
        setColor(Color.argb(238, 12, 17, 21));
        canvas.drawRect(0, top, width, height, paint);
        setStrokeColor(Color.rgb(63, 72, 75));
        paint.setStrokeWidth(1f * density);
        canvas.drawLine(0, top, width, top, paint);
    }

    private RectF roomBoard(float width, float height) {
        float top = headerHeight(width, height) + (isLandscape(width, height) ? 6f : 0f) * density;
        return new RectF(0, top, width, height - controlsPanelHeight(width, height));
    }

    private RectF roomBounds(Room room, float width, RectF board) {
        if (isLandscape(width, getHeight())) {
            int index = delegaciaRooms.indexOf(room);
            float margin = 16f * density;
            float gap = 9f * density;
            float roomWidth = (width - 2f * margin - gap * (delegaciaRooms.size() - 1))
                    / delegaciaRooms.size();
            float top = board.top + 23f * density;
            float bottom = board.bottom - 25f * density;
            float left = margin + index * (roomWidth + gap);
            return new RectF(left, top, left + roomWidth, bottom);
        }
        return new RectF(room.getX() * width,
                board.top + room.getY() * board.height(),
                (room.getX() + room.getWidth()) * width,
                board.top + (room.getY() + room.getHeight()) * board.height());
    }

    private String compactRoomName(Room room) {
        if (room.getName().contains("Recep")) {
            return "RECEPCAO";
        }
        if (room.getName().contains("Arsenal")) {
            return "ARSENAL";
        }
        return "CELAS";
    }

    private boolean isLandscape(float width, float height) {
        return width > height;
    }

    private float headerHeight(float width, float height) {
        return (isLandscape(width, height) ? 94f : 170f) * density;
    }

    private float controlsPanelHeight(float width, float height) {
        return (isLandscape(width, height) ? 120f : 190f) * density;
    }

    private synchronized void ensureControls(float width, float height) {
        int screenWidth = (int) width;
        int screenHeight = (int) height;
        if (btnPatrulha != null && controlsWidth == screenWidth && controlsHeight == screenHeight) {
            return;
        }

        controlsWidth = screenWidth;
        controlsHeight = screenHeight;
        boolean landscape = isLandscape(width, height);
        float buttonSize = (landscape ? 31f : 50f) * density;
        float gap = (landscape ? 4f : 6f) * density;
        float left = (landscape ? 24f : 24f) * density;
        float bottomInset = 24f * density;
        float downY = height - buttonSize - bottomInset;
        float middleY = downY - buttonSize - gap;
        float upperY = middleY - buttonSize - gap;
        btnLeft = new Button(left, middleY, buttonSize, buttonSize, "◀");
        btnRight = new Button(left + 2f * (buttonSize + gap), middleY,
            buttonSize, buttonSize, "▶");
        btnUp = new Button(left + buttonSize + gap, upperY, buttonSize, buttonSize, "▲");
        btnDown = new Button(left + buttonSize + gap, downY, buttonSize, buttonSize, "▼");

        float dpadRight = left + 3f * buttonSize + 2f * gap;
        float rightInset = (landscape ? 24f : 24f) * density;
        float minimumActionWidth = (landscape ? 100f : 104f) * density;
        float maximumActionWidth = (landscape ? 154f : 160f) * density;
        float actionWidth = Math.min(maximumActionWidth,
            Math.max(minimumActionWidth, width - dpadRight - 2f * rightInset));
        float actionHeight = (landscape ? 22f : 34f) * density;
        float actionGap = (landscape ? 4f : 6f) * density;
        float actionX = width - actionWidth - rightInset;
        float actionBottomY = height - bottomInset;
        btnPatrulha = new Button(actionX, actionBottomY - actionHeight,
            actionWidth, actionHeight, "PATRULHA");
        btnInvestigar = new Button(actionX, actionBottomY - 2f * actionHeight - actionGap,
            actionWidth, actionHeight, "INVESTIGAR");
        btnProximaFase = new Button(actionX, actionBottomY - 3f * actionHeight - 2f * actionGap,
            actionWidth, actionHeight, "AVANCAR FASE");
        btnHackear = new Button(actionX, actionBottomY - 4f * actionHeight - 3f * actionGap,
            actionWidth, actionHeight, "HACKEAR?");

        RectF board = roomBoard(width, height);
        if (landscape) {
            float frameWidth = Math.min(70f * density, width * 0.14f);
            float frameHeight = 16f * density;
            quadroEspingardaRect.set(width * 0.5f - frameWidth * 0.5f,
                    board.top + 2f * density,
                    width * 0.5f + frameWidth * 0.5f,
                    board.top + 2f * density + frameHeight);
        } else {
            float gapTop = board.top + board.height() * 0.635f;
            float gapBottom = board.top + board.height() * 0.71f;
            float frameWidth = Math.min(88f * density, width * 0.28f);
            float frameHeight = Math.max(12f * density, gapBottom - gapTop - 3f * density);
            float frameCenterY = (gapTop + gapBottom) * 0.5f;
            quadroEspingardaRect.set(width * 0.5f - frameWidth * 0.5f,
                    frameCenterY - frameHeight * 0.5f,
                    width * 0.5f + frameWidth * 0.5f,
                    frameCenterY + frameHeight * 0.5f);
        }
    }

    private void updateMovementButtons(MotionEvent event, int excludedPointerIndex) {
        boolean wasMoving = movingUp || movingDown || movingLeft || movingRight;
        boolean up = false;
        boolean down = false;
        boolean left = false;
        boolean right = false;
        for (int index = 0; index < event.getPointerCount(); index++) {
            if (index == excludedPointerIndex) {
                continue;
            }
            float x = event.getX(index);
            float y = event.getY(index);
            up |= btnUp.contains(x, y);
            down |= btnDown.contains(x, y);
            left |= btnLeft.contains(x, y);
            right |= btnRight.contains(x, y);
        }
        movingUp = up;
        movingDown = down;
        movingLeft = left;
        movingRight = right;
        if (!wasMoving && (up || down || left || right)) {
            showNpcDialogue("CAPITAO", npcInteractionManager.getFalaCapitao(),
                    "Capitao resmungando...");
        }
    }

    private void patrolStreets() {
        int ganhoDinheiro = (random.nextInt(200) + 100) * viaturasAtivas;
        int ganhoSucata = random.nextInt(15) + 5;
        int ganhoMoral = random.nextInt(5) + 2;
        dinheiro += ganhoDinheiro;
        sucata += ganhoSucata;
        moral += ganhoMoral;

        boolean capturedCriminal = isRoomUnlocked("Cela de Detenção")
            && celasOcupadas < capacidadeCelas && random.nextBoolean();
        if (capturedCriminal) {
            celasOcupadas++;
        }
        String rewardSummary = "Patrulha: +R$ " + ganhoDinheiro + ", +" + ganhoSucata
                + " sucata, +" + ganhoMoral + " moral"
                + (capturedCriminal ? " | Prisao realizada" : "");
        showNpcDialogue("CAPITAO", npcInteractionManager.getFalaCapitao(), rewardSummary);
    }

    private void investigate() {
        if (!isRoomUnlocked("Arsenal")) {
            showNpcDialogue("CAPITAO", "Precisamos reformar o Arsenal antes de contratar detetives.",
                    "Arsenal ainda em ruinas");
            return;
        }
        if (dinheiro < 300) {
            showNpcDialogue("CAPITAO", "Droga, falta dinheiro para custear a investigação!",
                    "Investigacao custa R$ 300");
            return;
        }
        dinheiro -= 300;
        detetivesContratados++;
        dinheiro += 600;
        showNpcDialogue("CAPITAO", "Investigação bem-sucedida! Lucramos com o caso.",
            "Detetive contratado; lucro de R$ 300");
    }

    private void advancePhase() {
        if (faseAtual == 1 && dinheiro >= 1000) {
            dinheiro -= 1000;
            faseAtual = 2;
            nomeFase = "Fase 2: Zona Portuaria";
            capacidadeCelas = 8;
                showNpcDialogue("CAPITAO", "Avançamos para a Zona Portuária! Preparem as algemas!",
                    "Nova fase: Zona Portuaria");
        } else if (faseAtual == 2 && dinheiro >= 3000) {
            dinheiro -= 3000;
            faseAtual = 3;
            nomeFase = "Fase 3: Suburbio Perigoso";
            capacidadeCelas = 12;
            viaturasAtivas = 2;
                showNpcDialogue("CAPITAO", "Subúrbio Perigoso liberado! Dobro de viaturas nas ruas!",
                    "Nova fase: Suburbio Perigoso");
        } else if (faseAtual >= 3) {
                showNpcDialogue("CAPITAO", "Todos os distritos foram protegidos. Bom trabalho, equipe!",
                    "Todas as fases foram concluidas");
        } else {
            int custo = faseAtual == 1 ? 1000 : 3000;
                showNpcDialogue("CAPITAO", "Ainda não temos verba suficiente para avançar de distrito.",
                    "Faltam R$ " + (custo - dinheiro));
        }
    }

    private boolean isRoomUnlocked(String roomName) {
        for (Room room : delegaciaRooms) {
            if (room.getName().equals(roomName)) {
                return room.isUnlocked();
            }
        }
        return false;
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color,
                          Paint.Align align, boolean bold) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size * density);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        canvas.drawText(text, x, y, paint);
    }

    private void setColor(int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
    }

    private void setStrokeColor(int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void resume() {
        isPlaying = true;
        startGameThreadIfReady();
    }

    public void pause() {
        isPlaying = false;
        Thread thread = gameThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        gameThread = null;
    }

    private synchronized void startGameThreadIfReady() {
        if (isPlaying && surfaceReady && (gameThread == null || !gameThread.isAlive())) {
            gameThread = new Thread(this, "DelegaciaRPG-GameLoop");
            gameThread.start();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startGameThreadIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        ensureControls(width, height);
        RectF board = roomBoard(width, height);
        if (playerX == 0 && playerY == 0) {
            playerX = width * 0.5f;
            playerY = board.centerY();
        } else {
            float radius = 16f * density;
            playerX = clamp(playerX, board.left + radius, board.right - radius);
            playerY = clamp(playerY, board.top + radius, board.bottom - radius);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        pause();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ensureControls(getWidth(), getHeight());
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();

        if (action == MotionEvent.ACTION_CANCEL) {
            movingUp = false;
            movingDown = false;
            movingLeft = false;
            movingRight = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            updateMovementButtons(event, pointerIndex);
            return true;
        }

        updateMovementButtons(event, -1);
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) {
            return true;
        }

        float touchX = event.getX(pointerIndex);
        float touchY = event.getY(pointerIndex);
        if (btnPatrulha.contains(touchX, touchY)) {
            patrolStreets();
            return true;
        }
        if (btnInvestigar.contains(touchX, touchY)) {
            investigate();
            return true;
        }
        if (btnProximaFase.contains(touchX, touchY)) {
            advancePhase();
            return true;
        }
        if (jogadorPertoDoQuadro && btnHackear.contains(touchX, touchY)) {
            dinheiro += 99999;
            sucata += 5000;
            moral += 1000;
            showNpcDialogue("CAPITAO", "Sistema hackeado com sucesso! Dinheiro infinito liberado!",
                    "Codigo secreto ativado");
            return true;
        }

        RectF board = roomBoard(getWidth(), getHeight());
        for (Room room : delegaciaRooms) {
            RectF bounds = roomBounds(room, getWidth(), board);
            if (!bounds.contains(touchX, touchY)) {
                continue;
            }

            if (room.isUnlocked()) {
                if (room.getName().equals("Arsenal") && detetivesContratados > 0) {
                    showNpcDialogue("DETETIVE", npcInteractionManager.getFalaDetetive(),
                            "Detetive falando");
                } else if (room.getName().equals("Cela de Detenção") && celasOcupadas > 0) {
                    showNpcDialogue("PRESO", npcInteractionManager.getFalaPreso(),
                            "Preso falando");
                } else {
                    statusMessage = room.getName() + " já foi reformado";
                }
            } else if (room.tentarReformar(sucata, moral)) {
                sucata -= room.getCostSucata();
                moral -= room.getCostMoral();
                statusMessage = room.getName() + " reformado!";
            } else {
                statusMessage = "Recursos insuficientes. Faça uma patrulha.";
            }
            return true;
        }

        return true;
    }
}