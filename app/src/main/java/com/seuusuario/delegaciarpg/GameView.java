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
            playerY = height * 0.72f;
        }

        float distanceToFrame = (float) Math.hypot(
            playerX - quadroEspingardaRect.centerX(),
            playerY - quadroEspingardaRect.centerY());
        jogadorPertoDoQuadro = distanceToFrame < 145f * density;

        float directionX = (movingRight ? 1f : 0f) - (movingLeft ? 1f : 0f);
        float directionY = (movingDown ? 1f : 0f) - (movingUp ? 1f : 0f);
        float directionLength = (float) Math.hypot(directionX, directionY);
        if (directionLength == 0) {
            return;
        }

        float speed = 220f * density * deltaSeconds;
        float radius = 16f * density;
        playerX = clamp(playerX + directionX / directionLength * speed, radius, width - radius);
        float minY = Math.max(300f * density, height * 0.34f);
        float maxY = Math.min(height - 100f * density, height * 0.82f);
        if (maxY < minY) {
            minY = radius;
            maxY = height - radius;
        }
        playerY = clamp(playerY + directionY / directionLength * speed, minY, maxY);
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
        drawHeader(canvas, width);
        drawRooms(canvas, width, height);
        drawWeaponFrame(canvas);
        drawPlayer(canvas);
        drawControls(canvas);
        drawDialogue(canvas, width, height);
    }

    private void drawHeader(Canvas canvas, float width) {
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

    private void drawRooms(Canvas canvas, float width, float height) {
        RectF board = roomBoard(width, height);
        for (Room room : delegaciaRooms) {
            RectF bounds = roomBounds(room, width, board);
            boolean unlocked = room.isUnlocked();
            setColor(unlocked ? Color.rgb(46, 105, 70) : Color.rgb(112, 47, 45));
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            setStrokeColor(unlocked ? Color.rgb(111, 190, 124) : Color.rgb(170, 91, 77));
            canvas.drawRoundRect(bounds, 10f * density, 10f * density, paint);
            paint.setStyle(Paint.Style.FILL);

            float left = bounds.left + 16f * density;
                drawText(canvas, room.getName(), left, bounds.top + 25f * density, 16f,
                    Color.WHITE, Paint.Align.LEFT, true);
            drawText(canvas, unlocked ? "REFORMADO" : "EM RUÍNAS", left,
                    bounds.top + 45f * density, 10f,
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
                drawText(canvas, roomStatus, left, bounds.bottom - 15f * density,
                        11f, Color.rgb(223, 236, 218), Paint.Align.LEFT, false);
            } else {
                drawText(canvas, "Custo: " + room.getCostSucata() + " sucata  |  "
                                + room.getCostMoral() + " moral",
                        left, bounds.bottom - 15f * density, 12f,
                        Color.rgb(245, 220, 194), Paint.Align.LEFT, false);
            }
        }
    }

    private void drawPlayer(Canvas canvas) {
        float radius = 13f * density;
        setColor(Color.rgb(68, 166, 207));
        canvas.drawCircle(playerX, playerY, radius, paint);
        setStrokeColor(Color.WHITE);
        paint.setStrokeWidth(2f * density);
        canvas.drawCircle(playerX, playerY, radius, paint);
        drawText(canvas, "POLICIAL", playerX, playerY - 23f * density, 9f,
                Color.WHITE, Paint.Align.CENTER, true);
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
        float boxY = playerY - 30f * density - boxHeight;
        float minY = 176f * density;
        float maxY = Math.max(minY, height - 175f * density - boxHeight);
        if (boxY < minY || boxY > maxY) {
            boxY = Math.max(minY, Math.min(playerY + 34f * density, maxY));
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

    private RectF roomBoard(float width, float height) {
        return new RectF(0, 170f * density, width, height - 165f * density);
    }

    private RectF roomBounds(Room room, float width, RectF board) {
        return new RectF(room.getX() * width,
                board.top + room.getY() * board.height(),
                (room.getX() + room.getWidth()) * width,
                board.top + (room.getY() + room.getHeight()) * board.height());
    }

    private synchronized void ensureControls(float width, float height) {
        int screenWidth = (int) width;
        int screenHeight = (int) height;
        if (btnPatrulha != null && controlsWidth == screenWidth && controlsHeight == screenHeight) {
            return;
        }

        controlsWidth = screenWidth;
        controlsHeight = screenHeight;
        float buttonSize = Math.min(50f * density, width * 0.15f);
        float gap = 6f * density;
        float left = 16f * density;
        float bottomY = height - buttonSize - 16f * density;
        btnLeft = new Button(left, bottomY, buttonSize, buttonSize, "◀");
        btnRight = new Button(left + 2f * (buttonSize + gap), bottomY,
                buttonSize, buttonSize, "▶");
        btnUp = new Button(left + buttonSize + gap, bottomY - buttonSize - gap,
                buttonSize, buttonSize, "▲");
        btnDown = new Button(left + buttonSize + gap, bottomY,
                buttonSize, buttonSize, "▼");

        float dpadRight = left + 3f * buttonSize + 2f * gap;
        float actionWidth = Math.min(136f * density,
            Math.max(76f * density, width - dpadRight - 24f * density));
        float actionHeight = 34f * density;
        float actionGap = 5f * density;
        float actionX = width - actionWidth - 14f * density;
        float actionBottomY = height - 16f * density;
        btnPatrulha = new Button(actionX, actionBottomY - actionHeight,
            actionWidth, actionHeight, "PATRULHA");
        btnInvestigar = new Button(actionX, actionBottomY - 2f * actionHeight - actionGap,
            actionWidth, actionHeight, "INVESTIGAR");
        btnProximaFase = new Button(actionX, actionBottomY - 3f * actionHeight - 2f * actionGap,
            actionWidth, actionHeight, "AVANCAR FASE");
        btnHackear = new Button(actionX, actionBottomY - 4f * actionHeight - 3f * actionGap,
            actionWidth, actionHeight, "HACKEAR?");

        RectF board = roomBoard(width, height);
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
        if (playerX == 0 && playerY == 0) {
            playerX = width * 0.5f;
            playerY = height * 0.72f;
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