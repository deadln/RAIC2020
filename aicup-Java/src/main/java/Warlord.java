import model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Warlord /*extends Thread*/ { // Управляет только боевыми юнитами
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    PlayerView playerView;
    ArrayList<Entity> entities;
    HashMap<Integer, EntityAction> result;
    HashSet<Integer> aliveEnemies;
    HashMap<Integer, Integer> enemyPositions;

    EntityType[] targets = new EntityType[] {EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.MELEE_BASE,
            EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET, EntityType.WALL, EntityType.BUILDER_UNIT};
    EntityType[] turretTargets = new EntityType[] {EntityType.RANGED_UNIT, EntityType.MELEE_UNIT, EntityType.MELEE_BASE,
            EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET, EntityType.WALL, EntityType.BUILDER_UNIT};
    ArrayList<Entity> buildings;
    int[][] filledCells;
    HashMap<Integer, Entity> entityById;

    int RED_ALERT_RADIUS = 10;

    public Warlord() {
    }

    public void setActive(int active) {
        this.active = active;
    }

    public int getActive() {
        return active;
    }

    double getDistance(Vec2Int a, Vec2Int b){ // Расстояние между точками
        return Math.hypot(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    Vec2Int getAttackPoint(int position){
        if (position == 0){
            return new Vec2Int(
                    playerView.getMapSize() - 1,
                    playerView.getMapSize() - 1
            );
        }
        if (position == 1){
            return new Vec2Int(
                    playerView.getMapSize() - 1,
                    0
            );
        }
        if (position == 2){
            return new Vec2Int(
                    0,
                    playerView.getMapSize() - 1
            );
        }
        return new Vec2Int(
                playerView.getMapSize() - 1,
                playerView.getMapSize() - 1
        );
    }

    Entity getEnemyNearby(Entity building){
        var size = playerView.getEntityProperties().get(building.getEntityType()).getSize();
        Entity nearestEnemy = null;
        double distance = playerView.getMapSize();


        for(int i = building.getPosition().getX() - RED_ALERT_RADIUS - 1;i < building.getPosition().getX() + size + RED_ALERT_RADIUS; i++){
            if(i < 0 || i >= playerView.getMapSize())
                continue;
            for(int j = building.getPosition().getY() + size + RED_ALERT_RADIUS ;j > building.getPosition().getY() - RED_ALERT_RADIUS - 1; j--){
                if(j < 0 || j >= playerView.getMapSize())
                    continue;
                //if(i*i + j*j <= RED_ALERT_RADIUS*RED_ALERT_RADIUS){
                    /*if(filledCells[i][j] != 0)
                        System.out.println(filledCells[i][j]);*/
                    Entity enemy = entityById.get(filledCells[i][j]);
                    if(enemy != null && enemy.getPlayerId() != null && enemy.getPlayerId() != playerView.getMyId()){
                        System.out.println("Supposed to be enemy: " + enemy.getPosition().getX() + " " + enemy.getPosition().getY());
                    }
                    if(enemy == null || enemy.getPlayerId() == null || enemy.getPlayerId() == playerView.getMyId() ||
                            (enemy.getEntityType() != EntityType.RANGED_UNIT && enemy.getEntityType() != EntityType.MELEE_UNIT)){
                        //System.out.println("Not enemy");
                        continue;}
                    System.out.println("Enemy: " + enemy.getPosition().getX() + " " + enemy.getPosition().getY());
                    double dis = getDistance(new Vec2Int(building.getPosition().getX() + size - 1,
                            building.getPosition().getY() + size - 1), enemy.getPosition());
                    if(enemy != null && dis < distance){
                        nearestEnemy = enemy;
                        distance = dis;
                    }
                //}
            }
        }
        if(nearestEnemy != null)
            System.out.println("Found enemy at " + nearestEnemy.getPosition().getX() + " " + nearestEnemy.getPosition().getY());
        return nearestEnemy;
    }

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }

    public void activate(PlayerView playerView, ArrayList<Entity> warlordEntities, HashSet<Integer> aliveEnemies,
                         HashMap<Integer, Integer> enemyPositions, ArrayList<Entity> buildings, int[][] filledCells,
                         HashMap<Integer, Entity> entityById){
        this.playerView = playerView;
        this.entities = warlordEntities;
        this.aliveEnemies = aliveEnemies;
        this.enemyPositions = enemyPositions;
        this.buildings = buildings;
        this.filledCells = filledCells;
        this.entityById = entityById;

        result = new HashMap<>();

        Entity nearestEnemy = null;
        double distance = playerView.getMapSize();
        for(var building : buildings){ //Проход по зданиям
            Entity enemy = getEnemyNearby(building);
            if(enemy == null)
                continue;
            double dis = getDistance(new Vec2Int(0,0), enemy.getPosition());
            if(enemy != null && dis < distance){
                nearestEnemy = enemy;
                distance = dis;
            }
        }

        for(var entity : entities){
            var properties = playerView.getEntityProperties().get(entity.getEntityType());

            MoveAction moveAction = null;
            if(nearestEnemy != null && entity.getPosition().getX() < playerView.getMapSize() / 2 &&
                    entity.getPosition().getY() < playerView.getMapSize() / 2){ //Признак "Враг у ворот"
                System.out.println("RED ALERT");
                moveAction = new MoveAction(
                        nearestEnemy.getPosition(),
                        true,
                        true
                );
            }
            else if(aliveEnemies != null) {
                for (int position = 0; position < 3; position++) {
                    if (aliveEnemies.contains(enemyPositions.get(position)) == true) {
                        moveAction = new MoveAction(getAttackPoint(position), true, true);
                        //System.out.println("Attack " + position);
                        break;
                    }
                }
            }
            else {
                //System.out.println("Attack default");
                moveAction = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                        playerView.getMapSize() - 1), true, true);
            }
            BuildAction buildAction = null;
            AttackAction attackAction = new AttackAction(
                    null,
                    new AutoAttack(
                            properties.getSightRange(),
                            turretTargets
                    )
            );

            if(entity.getEntityType() == EntityType.TURRET) { // Турель
                moveAction = null;
                attackAction = new AttackAction(
                        null,
                        new AutoAttack(
                                properties.getSightRange(),
                                turretTargets
                        )
                );
            }

            result.put(
                    entity.getId(),
                    new EntityAction(
                            moveAction,
                            buildAction,
                            attackAction,
                            null
                    )
            );
        }
        active = 2;
    }
}
