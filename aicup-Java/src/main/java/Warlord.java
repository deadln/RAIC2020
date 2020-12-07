import model.*;

import java.util.ArrayList; // милишник = 1
import java.util.HashMap; // лучник = 1.1
import java.util.HashSet; // Турель = 3.3

//TODO Функция для определения ближайшего юнита

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

    //Переменные
    int BASE_RED_ALERT_RADIUS = 15;
    int UNIT_RED_ALERT_RADIUS = 7;
    double MEELE_POWER = 1;
    double RANGE_POWER = 1.1;
    double TURRET_POWER = 3.3;
    double DIFFERENCE_TO_ATTACK = 5;

//3 (788 откр) (984 120302 узк) (827 94874 откр) (804 107413 откр)
    //5 (919 123840 укз) (681 87886 откр)
//7 (973 149168 узк) (806 93936 откр)
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
        Vec2Int corner = null;
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

        if(corner == null)
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

    Entity getEnemyNearby(Entity building, int radius){
        var size = playerView.getEntityProperties().get(building.getEntityType()).getSize();
        Entity nearestEnemy = null;
        double distance = playerView.getMapSize();


        for(int i = building.getPosition().getX() - radius - 1;i < building.getPosition().getX() + size + radius; i++){
            if(i < 0 || i >= playerView.getMapSize())
                continue;
            for(int j = building.getPosition().getY() + size + radius ;j > building.getPosition().getY() - radius - 1; j--){
                if(j < 0 || j >= playerView.getMapSize())
                    continue;
                //if(i*i + j*j <= RED_ALERT_RADIUS*RED_ALERT_RADIUS){*/
                    Entity enemy = entityById.get(filledCells[i][j]);

                    if(enemy == null || enemy.getPlayerId() == null || enemy.getPlayerId() == playerView.getMyId() ||
                            (enemy.getEntityType() != EntityType.RANGED_UNIT &&
                                    enemy.getEntityType() != EntityType.MELEE_UNIT &&
                                    enemy.getEntityType() != EntityType.BUILDER_UNIT)){
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

    HashMap<Integer, Double> getPlayersPower(){ // Оценка сил армий противников
        HashMap<Integer, Double> result = new HashMap<>();
        ArrayList<Entity> list = new ArrayList<>();
        list.addAll(entities);
        list.addAll(enemyEntities);

        for(var player : playerView.getPlayers())
            result.put(player.getId(), 0.0);

        for(var entity : list){
            if(entity.getEntityType() == EntityType.MELEE_UNIT && (entity.getPlayerId() == playerView.getMyId() ||
                    enemyAtBase(entity)))
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + MEELE_POWER);
            else if(entity.getEntityType() == EntityType.RANGED_UNIT && (entity.getPlayerId() == playerView.getMyId() ||
                    enemyAtBase(entity)))
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + RANGE_POWER);
            else if(entity.getEntityType() == EntityType.TURRET && entity.getPlayerId() != playerView.getMyId())
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + TURRET_POWER);
        }

        return result;
    }

    boolean enemyAtBase(Entity entity){
        int RANGE_OF_BASE = 27;
        int position = -1;
        for(int i = 0; i < 3; i++){
            if(enemyPositions.get(i) == entity.getPlayerId()){
                position = i;
                break;
            }
        }
        if(position == 0 && getDistance(new Vec2Int(playerView.getMapSize(), playerView.getMapSize()),
                entity.getPosition()) < RANGE_OF_BASE){
            return true;
        }
        if(position == 1 && getDistance(new Vec2Int(playerView.getMapSize(), 0),
                entity.getPosition()) < RANGE_OF_BASE){
            return true;
        }
        if(position == 2 && getDistance(new Vec2Int(0, playerView.getMapSize()),
                entity.getPosition()) < RANGE_OF_BASE){
            return true;
        }
        return false;
    }

    Vec2Int isFarAway(Entity unit){
        int x = unit.getPosition().getX();
        int y = unit.getPosition().getY();

        if(x != 0 && y != 0 && x != playerView.getMapSize() - 1 && y != playerView.getMapSize() - 1)
        {
            if(isOurWarrior(entityById.get(filledCells[x + 1][y + 1])) || isOurWarrior(entityById.get(filledCells[x + 1][y])) ||
                    isOurWarrior(entityById.get(filledCells[x + 1][y - 1])) || isOurWarrior(entityById.get(filledCells[x][y - 1])) ||
                    isOurWarrior(entityById.get(filledCells[x - 1][y - 1])) || isOurWarrior(entityById.get(filledCells[x - 1][y])) ||
                    isOurWarrior(entityById.get(filledCells[x - 1][y + 1])) || isOurWarrior(entityById.get(filledCells[x][y + 1])))
                return null;
        }

        double distance = 9000000, dis;
        Vec2Int ourPositions = null;
        for(var entity : entities){
            if(!isOurWarrior(entity))
                continue;
            dis = getDistance(unit.getPosition(), entity.getPosition());
            if(dis < distance){
                distance = dis;
                ourPositions = entity.getPosition();
            }
        }
        return ourPositions;
    }

    boolean isOurWarrior(Entity entity){
        if(entity != null && entity.getPlayerId() != null && entity.getPlayerId() == playerView.getMyId() &&
                (entity.getEntityType() == EntityType.MELEE_UNIT || entity.getEntityType() == EntityType.RANGED_UNIT))
            return true;
        return false;
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
        /*for(var player : playerView.getPlayers()) {
            System.out.println("PLAYER: " + player.getId());
            System.out.println("RESOURCE: " + player.getResource());
            System.out.println("POWER: " + playersPower.get(player.getId()));
            System.out.println();
        }*/

        result = new HashMap<>();

        //Обнаружение угрозы
        Entity nearestEnemy = null;
        double distance = playerView.getMapSize();

        for(var entity : entities){ //Проход по юнитам
            if(entity.getPosition().getX() > 25 && entity.getPosition().getY() > 25)
                continue;
            Entity enemy = getEnemyNearby(entity, UNIT_RED_ALERT_RADIUS);
            if(enemy == null)
                continue;
            double dis = getDistance(new Vec2Int(0,0), enemy.getPosition());
            if(enemy != null && dis < distance){
                nearestEnemy = enemy;
                distance = dis;
            }
        }

        distance = playerView.getMapSize();
        for(var building : buildings){ //Проход по зданиям
            Entity enemy = getEnemyNearby(building, BASE_RED_ALERT_RADIUS);
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
            BuildAction buildAction = null;
            AttackAction attackAction = new AttackAction(
                    null,
                    new AutoAttack(
                            properties.getSightRange(),
                            turretTargets
                    )
            );

            if(nearestEnemy != null /*&& entity.getPosition().getX() < playerView.getMapSize() / 2 &&
                    entity.getPosition().getY() < playerView.getMapSize() / 2*/){ //Признак "Враг у ворот"
                //System.out.println("RED ALERT");
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
                if(target == -1){ // Скопление на точке сбора
                    moveAction = new MoveAction(new Vec2Int(16,16), true, false);
                }
                else{ // Атака на противника
                    var attackPoint = getAttackPoint(attackPosition, target);
                    /*System.out.println("TARGET: " + target);
                    System.out.println("Nearest enemy: " + attackPoint.getX() + " " + attackPoint.getY());*/
                    moveAction = new MoveAction(attackPoint, true, true);
                }
            }
            //Перегруппировка
            var ourPositions = isFarAway(entity);
            if(ourPositions != null){
                if(getDistance(ourPositions, entity.getPosition()) > 2){
                    moveAction = new MoveAction(ourPositions, true, true);
                    if(entity.getEntityType() == EntityType.RANGED_UNIT)
                        attackAction = null;

                }
            }


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
