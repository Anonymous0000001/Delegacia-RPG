package com.seuusuario.delegaciarpg;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import java.util.ArrayList;
import java.util.List;

public class GameScreen implements Screen, InputProcessor {
    private final Vector3 playerPosition = new Vector3(0f, 1.5f, 0f);
    private final Vector3 targetPosition = new Vector3(playerPosition);
    private final Vector3 movement = new Vector3();
    private final Vector3 touchPosition = new Vector3();
    private final Plane floorPlane = new Plane(Vector3.Y, 0f);
    private final BoundingBox viaturaBounds = new BoundingBox();
    private final BoundingBox quadroBounds = new BoundingBox();
    private final List<RoomVisual> roomVisuals = new ArrayList<>();

    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private BitmapFont font;
    private Environment environment;

    private Model floorModel;
    private Model playerModel;
    private Model viaturaModel;
    private Model quadroModel;
    private ModelInstance floorInstance;
    private ModelInstance playerInstance;
    private ModelInstance viaturaInstance;
    private ModelInstance quadroInstance;

    private int dinheiro = 1500;
    private int sucata = 50;
    private int moral = 30;
    private String statusMessage = "Toque no chao para mover o policial";

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,
                0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(
                0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);

        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1f;
        camera.far = 300f;
        updateCamera();

        ModelBuilder modelBuilder = new ModelBuilder();
        floorModel = modelBuilder.createBox(24f, 0.5f, 24f,
                new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)),
                Usage.Position | Usage.Normal);
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setTranslation(0f, -0.25f, 0f);

        playerModel = modelBuilder.createCapsule(0.5f, 3f, 16,
                new Material(ColorAttribute.createDiffuse(Color.NAVY)),
                Usage.Position | Usage.Normal);
        playerInstance = new ModelInstance(playerModel);
        playerInstance.transform.setTranslation(playerPosition);

        viaturaModel = modelBuilder.createBox(4f, 2f, 7f,
                new Material(ColorAttribute.createDiffuse(Color.BLACK)),
                Usage.Position | Usage.Normal);
        viaturaInstance = new ModelInstance(viaturaModel);
        viaturaInstance.transform.setTranslation(6f, 1f, -3f);

        quadroModel = modelBuilder.createBox(3f, 2f, 0.4f,
            new Material(ColorAttribute.createDiffuse(Color.FIREBRICK)),
            Usage.Position | Usage.Normal);
        quadroInstance = new ModelInstance(quadroModel);
        quadroInstance.transform.setTranslation(-6f, 3f, -8f);

        createRoom(modelBuilder, "Recepcao", 30, 10, -9f, 5f, 5.5f, 4f);
        createRoom(modelBuilder, "Arsenal", 60, 25, -2.75f, 5f, 5.5f, 4f);
        createRoom(modelBuilder, "Cela", 100, 40, 3.5f, 5f, 5.5f, 4f);

        viaturaBounds.set(new Vector3(4f, 0f, -6.5f), new Vector3(8f, 2f, 0.5f));
        quadroBounds.set(new Vector3(-7.5f, 2f, -8.2f), new Vector3(-4.5f, 4f, -7.8f));

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    @Override
    public void render(float delta) {
        updatePlayer(Math.min(delta, 0.05f));
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(floorInstance, environment);
        modelBatch.render(playerInstance, environment);
        modelBatch.render(viaturaInstance, environment);
        modelBatch.render(quadroInstance, environment);
        for (RoomVisual roomVisual : roomVisuals) {
            modelBatch.render(roomVisual.instance, environment);
        }
        modelBatch.end();

        spriteBatch.begin();
        font.draw(spriteBatch, "Delegacia RPG", 24f, Gdx.graphics.getHeight() - 24f);
        font.draw(spriteBatch, "R$ " + dinheiro + "    Sucata: " + sucata + "    Moral: " + moral,
                24f, Gdx.graphics.getHeight() - 48f);
        font.draw(spriteBatch, statusMessage,
                24f, 28f);
        spriteBatch.end();
    }

    private void updatePlayer(float delta) {
        movement.set(targetPosition).sub(playerPosition);
        float distance = movement.len();
        if (distance > 0.08f) {
            movement.scl(Math.min(distance, 4f * delta) / distance);
            playerPosition.add(movement);
            playerInstance.transform.setTranslation(playerPosition);
        }
        updateCamera();
    }

    private void createRoom(ModelBuilder modelBuilder, String name, int scrapCost, int moralCost,
                            float x, float z, float width, float depth) {
        Room room = new Room(name, scrapCost, moralCost, x, z, width, depth);
        Color roomColor = roomColor(room.isUnlocked());
        Model roomModel = modelBuilder.createBox(width, 0.5f, depth,
                new Material(ColorAttribute.createDiffuse(roomColor)),
                Usage.Position | Usage.Normal);
        ModelInstance roomInstance = new ModelInstance(roomModel);
        roomInstance.transform.setTranslation(x + width * 0.5f, 0.25f, z + depth * 0.5f);
        BoundingBox roomBounds = new BoundingBox(
                new Vector3(x, 0f, z), new Vector3(x + width, 0.5f, z + depth));
        roomVisuals.add(new RoomVisual(room, roomModel, roomInstance, roomBounds));
    }

    private Color roomColor(boolean isUnlocked) {
        return isUnlocked ? Color.FOREST.cpy() : Color.FIREBRICK.cpy();
    }

    private void updateCamera() {
        camera.position.set(playerPosition.x, 15f, playerPosition.z + 15f);
        camera.lookAt(playerPosition.x, 0f, playerPosition.z);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Ray ray = camera.getPickRay(screenX, screenY);
        if (Intersector.intersectRayBoundsFast(ray, viaturaBounds)) {
            dinheiro += 300;
            statusMessage = "Patrulha concluida: +R$ 300";
            return true;
        }
        if (Intersector.intersectRayBoundsFast(ray, quadroBounds)) {
            dinheiro += 50000;
            sucata += 1000;
            statusMessage = "Quadro inspecionado: recursos adicionados";
            return true;
        }
        for (RoomVisual roomVisual : roomVisuals) {
            if (!Intersector.intersectRayBoundsFast(ray, roomVisual.bounds)) {
                continue;
            }
            Room room = roomVisual.room;
            if (room.tentarReformar(sucata, moral)) {
                sucata -= room.getCostSucata();
                moral -= room.getCostMoral();
                roomVisual.instance.materials.first().set(
                        ColorAttribute.createDiffuse(roomColor(true)));
                statusMessage = room.getName() + " reformado!";
            } else if (room.isUnlocked()) {
                statusMessage = room.getName() + " ja foi reformado";
            } else {
                statusMessage = "Recursos insuficientes para " + room.getName();
            }
            return true;
        }
        if (Intersector.intersectRayPlane(ray, floorPlane, touchPosition)) {
            targetPosition.set(
                    Math.max(-10f, Math.min(10f, touchPosition.x)),
                    playerPosition.y,
                    Math.max(-10f, Math.min(10f, touchPosition.z)));
            statusMessage = "Policial a caminho do local";
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public void resize(int width, int height) {
        if (height == 0) {
            return;
        }
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        updateCamera();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        if (modelBatch != null) {
            modelBatch.dispose();
        }
        if (spriteBatch != null) {
            spriteBatch.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (floorModel != null) {
            floorModel.dispose();
        }
        if (playerModel != null) {
            playerModel.dispose();
        }
        if (viaturaModel != null) {
            viaturaModel.dispose();
        }
        if (quadroModel != null) {
            quadroModel.dispose();
        }
        for (RoomVisual roomVisual : roomVisuals) {
            roomVisual.model.dispose();
        }
    }

    private static final class RoomVisual {
        private final Room room;
        private final Model model;
        private final ModelInstance instance;
        private final BoundingBox bounds;

        private RoomVisual(Room room, Model model, ModelInstance instance, BoundingBox bounds) {
            this.room = room;
            this.model = model;
            this.instance = instance;
            this.bounds = bounds;
        }
    }
}