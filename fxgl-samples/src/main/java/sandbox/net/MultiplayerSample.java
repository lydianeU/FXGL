/*
 * FXGL - JavaFX Game Library. The MIT License (MIT).
 * Copyright (c) AlmasB (almaslvl@gmail.com).
 * See LICENSE for details.
 */

package sandbox.net;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.core.serialization.Bundle;
import com.almasb.fxgl.dsl.components.OffscreenCleanComponent;
import com.almasb.fxgl.dsl.components.ProjectileComponent;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.input.Input;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.multiplayer.MultiplayerService;
import com.almasb.fxgl.multiplayer.NetworkComponent;
import com.almasb.fxgl.net.Connection;
import com.almasb.fxgl.net.Server;
import com.almasb.fxgl.particle.ParticleComponent;
import com.almasb.fxgl.particle.ParticleEmitters;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;
import static javafx.scene.input.KeyCode.*;

/**
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
public class MultiplayerSample extends GameApplication {

    private boolean isServer = false;

    private Server<Bundle> server;
    private Connection<Bundle> connection;

    private Entity player1;
    private Entity player2;

    private Input clientInput;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.addEngineService(MultiplayerService.class);
    }

    @Override
    protected void initInput() {
        onKey(W, () -> player1.translateY(-5));
        onKey(S, () -> player1.translateY(5));
        onKey(A, () -> player1.translateX(-5));
        onKey(D, () -> player1.translateX(5));
        onBtnDown(MouseButton.PRIMARY, () -> shoot(player1));

        clientInput = new Input();

        onKeyBuilder(clientInput, W)
                .onAction(() -> player2.translateY(-5));
        onKeyBuilder(clientInput, S)
                .onAction(() -> player2.translateY(5));
        onKeyBuilder(clientInput, A)
                .onAction(() -> player2.translateX(-5));
        onKeyBuilder(clientInput, D)
                .onAction(() -> player2.translateX(5));

        clientInput.addAction(new UserAction("Shoot") {
            @Override
            protected void onActionBegin() {
                shoot(player2);
            }
        }, MouseButton.PRIMARY);
    }

    @Override
    protected void initGame() {
        getGameWorld().addEntityFactory(new MultiplayerFactory());

        getGameScene().setBackgroundColor(Color.LIGHTGRAY);

        runOnce(() -> {

            getDialogService().showConfirmationBox("Is Server?", answer -> {
                isServer = answer;

                if (isServer) {
                    // TODO: have only server init and only client init code to override

                    onCollisionBegin(EntityType.BULLET, EntityType.ENEMY, (bullet, enemy) -> {
                        bullet.removeFromWorld();
                        enemy.removeFromWorld();
                    });

                    server = getNetService().newTCPServer(55555);
                    server.setOnConnected(conn -> {
                        connection = conn;

                        getExecutor().startAsyncFX(() -> {
                            player1 = spawn("player1", 150, 150);
                            getMPService().spawn(connection, player1, "player1");

                            player2 = spawn("player2", 150, 250);
                            getMPService().spawn(connection, player2, "player2");

                            getMPService().addInputReplicationReceiver(conn, clientInput);
                        });
                    });

                    server.startAsync();

//                    getTaskService().runAsync(
//                            server.startTask()
//                                    .onSuccess(n -> System.out.println("Server startTask success"))
//                                    .onFailure(e -> System.out.println("Server startTask fail: " + e))
//                    );

                } else {

                    var client = getNetService().newTCPClient("localhost", 55555);
                    client.setOnConnected(conn -> {
                        getMPService().addEntityReplicationReceiver(conn, getGameWorld());
                        getMPService().addInputReplicationSender(conn, getInput());
                    });

                    client.connectAsync();

//                    getTaskService().runAsync(
//                            client.connectTask()
//                                    .onSuccess(n -> System.out.println("client connectTask success"))
//                                    .onFailure(e -> System.out.println("client connectTask fail: " + e))
//                    );

                    getInput().setProcessInput(false);
                }

                doInit();
            });

        }, Duration.seconds(0.5));
    }

    private MultiplayerService getMPService() {
        return getService(MultiplayerService.class);
    }

    private void doInit() {
        run(() -> {
            if (isServer && connection != null) {
                var point = FXGLMath.randomPoint(new Rectangle2D(600, 0, 100, 500));

                var e = spawn("enemy", point);

                getMPService().spawn(connection, e, "enemy");
            }
        }, Duration.seconds(1));
    }

    @Override
    protected void onUpdate(double tpf) {
        if (clientInput != null) {
            clientInput.update(tpf);
        }

//        if (isServer && connection != null) {
//            var bundle = new Bundle("pos");
//            bundle.put("x", player1.getX());
//            bundle.put("y", player1.getY());
//
//            server.broadcast(bundle);
//        }
    }

    private void shoot(Entity entity) {
        var bullet = spawn("bullet", entity.getPosition());

        getMPService().spawn(connection, bullet, "bullet");
    }

    private enum EntityType {
        BULLET, ENEMY
    }

    public static class MultiplayerFactory implements EntityFactory {

        @Spawns("player1")
        public Entity newPlayer1(SpawnData data) {
            return entityBuilder(data)
                    .view(new Rectangle(40, 40, Color.BLUE))
                    .with(new NetworkComponent())
                    .build();
        }

        @Spawns("player2")
        public Entity newPlayer2(SpawnData data) {
            return entityBuilder(data)
                    .view(new Rectangle(40, 40, Color.DARKGREEN))
                    .with(new NetworkComponent())
                    .build();
        }

        @Spawns("bullet")
        public Entity newBullet(SpawnData data) {
            return entityBuilder(data)
                    .type(EntityType.BULLET)
                    .viewWithBBox(new Rectangle(10, 2, Color.BROWN))
                    .collidable()
                    .with(new OffscreenCleanComponent())
                    .with(new ProjectileComponent(new Point2D(1, 0), 500))
                    .with(new NetworkComponent())
                    .build();
        }

        @Spawns("enemy")
        public Entity newEnemy(SpawnData data) {
            return entityBuilder(data)
                    .type(EntityType.ENEMY)
                    .viewWithBBox(new Rectangle(20, 20, Color.RED))
                    .collidable()
                    .with(new ProjectileComponent(new Point2D(-1, 0), 10))
                    .with(new NetworkComponent())
                    .onNotActive(e -> {
                        var particles = entityBuilder()
                                .at(e.getCenter())
                                .buildAndAttach();

                        var emitter = ParticleEmitters.newExplosionEmitter(150);

                        var comp = new ParticleComponent(emitter);
                        comp.setOnFinished(() -> particles.removeFromWorld());

                        particles.addComponent(comp);
                    })
                    .build();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
