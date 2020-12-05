import model.*;

import java.util.ArrayList; // милишник = 1
import java.util.HashMap; // лучник = 1.1
import java.util.HashSet; // Турель = 3.3

public class Warlord /*extends Thread*/ { // Управляет только боевыми юнитами
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу
    boolean redAlert = false;

    PlayerView playerView;
    ArrayList<Entity> entities;
    ArrayList<Entity> enemyEntities;
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

    HashMap<Integer, Double> playersPower;

    int RED_ALERT_RADIUS = 10;
    double MEELE_POWER = 1;
    double RANGE_POWER = 1.1;
    double TURRET_POWER = 3.3;
    double DIFFERENCE_TO_ATTACK = 3;



    public Warlord() {
    }

    public void setActive(int active) {
        this.active = active;
    }

    public int getActive() {
        return active;
    }

    public boolean isRedAlert() {
        return redAlert;
    }

    public void setRedAlert(boolean redAlert) {
        this.redAlert = redAlert;
    }

    double getDistance(Vec2Int a, Vec2Int b){ // Расстояние между точками
        return Math.hypot(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    Vec2Int getAttackPoint(int position, int targetId){
        //Поиск угла противника
        Vec2Int corner;
        int mod_x = 0;
        int mod_y = 0;
        if (position == 0){ // Правый верхний угол
            corner = new Vec2Int(
                    playerView.getMapSize() - 1,
                    playerView.getMapSize() - 1
            );
            mod_x = mod_y = playerView.getMapSize() - 1;
        }
        else if (position == 1){ // Правый нижний угол
            corner = new Vec2Int(
                    playerView.getMapSize() - 1,
                    0
            );
            mod_x = playerView.getMapSize() - 1;
        }
        else if (position == 2){ // Левый верхний угол
            corner = new Vec2Int(
                    0,
                    playerView.getMapSize() - 1
            );
            mod_y = playerView.getMapSize() - 1;
        }


        corner = new Vec2Int(20,20);

        //Поиск ближайшего к углу юнита
        double distance = 9000000000.0, dis;
        Vec2Int targetLocation = null;
        for(var entity : enemyEntities){
            if(entity.getPlayerId() != targetId)
                continue;
            dis = getDistance(corner, entity.getPosition());
            if(dis < distance){
                targetLocation = entity.getPosition();
                distance = dis;
            }
        }

        if(targetLocation != null)
            return targetLocation;
        return new Vec2Int(20,20);
        /*for(int d = 0; d < playerView.getMapSize(); d++) {
            for (int i = 0; i < d / 2 + 1; i++) {
                if(filledCells[mod_x - i][mod_y - d - i] != 0 && entityById.get(filledCells[mod_x - i][mod_y - d - i]).getPlayerId() != null &&
                        entityById.get(filledCells[mod_x - i][mod_y - d - i]).getPlayerId() == targetId)
                    return new Vec2Int(mod_x - i,mod_y - d - i);
                if(filledCells[mod_x - d - i][mod_y - i] != 0 && entityById.get(filledCells[mod_x - d - i][mod_y - i]).getPlayerId() != null &&
                        entityById.get(filledCells[mod_x - d - i][mod_y - i]).getPlayerId() == targetId)
                    return new Vec2Int(mod_x - d - i,mod_y - i);
            }
        }*/
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
                //if(i*i + j*j <= RED_ALERT_RADIUS*RED_ALERT_RADIUS){*/
                    Entity enemy = entityById.get(filledCells[i][j]);

                    if(enemy == null || enemy.getPlayerId() == null || enemy.getPlayerId() == playerView.getMyId() ||
                            (enemy.getEntityType() != EntityType.RANGED_UNIT && enemy.getEntityType() != EntityType.MELEE_UNIT)){
                        continue;}
                    double dis = getDistance(new Vec2Int(building.getPosition().getX() + size - 1,
                            building.getPosition().getY() + size - 1), enemy.getPosition());
                    if(enemy != null && dis < distance){
                        nearestEnemy = enemy;
                        distance = dis;
                    }
                //}
            }
        }
        return nearestEnemy;
    }

    HashMap<Integer, Double> getPlayersPower(){
        HashMap<Integer, Double> result = new HashMap<>();
        ArrayList<Entity> list = new ArrayList<>();
        list.addAll(entities);
        list.addAll(enemyEntities);

        for(var player : playerView.getPlayers())
            result.put(player.getId(), 0.0);

        for(var entity : list){
            if(entity.getEntityType() == EntityType.MELEE_UNIT)
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + MEELE_POWER);
            else if(entity.getEntityType() == EntityType.RANGED_UNIT)
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + RANGE_POWER);
            else if(entity.getEntityType() == EntityType.TURRET)
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + TURRET_POWER);
        }

        return result;
    }

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }

    public void activate(PlayerView playerView, ArrayList<Entity> warlordEntities, HashSet<Integer> aliveEnemies,
                         HashMap<Integer, Integer> enemyPositions, ArrayList<Entity> buildings, int[][] filledCells,
                         HashMap<Integer, Entity> entityById, ArrayList<Entity> enemyEntities){
        this.playerView = playerView;
        this.entities = warlordEntities;
        this.aliveEnemies = aliveEnemies;
        this.enemyPositions = enemyPositions;
        this.buildings = buildings;
        this.filledCells = filledCells;
        this.entityById = entityById;
        this.enemyEntities = enemyEntities;

        this.playersPower = getPlayersPower();
        for(var player : playerView.getPlayers()) {
            System.out.println("PLAYER: " + player.getId());
            System.out.println("RESOURCE: " + player.getResource());
            System.out.println("POWER: " + playersPower.get(player.getId()));
            System.out.println();
        }

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

        if(nearestEnemy != null)
            redAlert = true;
        else
            redAlert = false;

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
            //Приказ на атаку
            else if(aliveEnemies != null) {
                double power = 90000000, pow; // IT"S OVER NINE THOUSAND!
                int target = -1;
                int attackPosition = -1;
                for (int position = 0; position < 3; position++) {
                    int enemyId = enemyPositions.get(position);
                    if (aliveEnemies.contains(enemyId)) { // Если враг жив
                        pow = playersPower.get(enemyId);
                        if(playersPower.get(playerView.getMyId()) - pow >= DIFFERENCE_TO_ATTACK)
                        {
                            target = enemyId;
                            attackPosition = position;
                        }
                    }
                }
                if(target == -1){
                    moveAction = new MoveAction(new Vec2Int(20,20), true, false);
                }
                else{
                    var attackPoint = getAttackPoint(attackPosition, target);
                    System.out.println("TARGET: " + target);
                    System.out.println("Nearest enemy: " + attackPoint.getX() + " " + attackPoint.getY());
                    moveAction = new MoveAction(attackPoint, true, true);
                }
            }
            else {
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
