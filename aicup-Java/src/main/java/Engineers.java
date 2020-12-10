import model.*;

import java.util.ArrayList;
import java.util.HashMap;

public class Engineers {
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    Entity eastEngineer;
    Entity northEngineer;

    PlayerView playerView;
    HashMap<Integer, Entity> entityById;
    int[][] filledCells;
    ArrayList<Entity> turrets;
    ArrayList<Entity> enemyEntities;
    HashMap<Integer, Integer> enemyPositions;
    HashMap<Integer, Double> playersPower;
    Player me;
    HashMap<Integer, EntityAction> result;

    int TIME_TO_BUILD = 200;
    int LINE_OF_DEFENCE = 28;
    int TURRET_MIN_RADIUS = 1;
    double MEELE_POWER = 1;
    double RANGE_POWER = 1.1;
    double TURRET_POWER = 3.3;

    public Engineers() {
    }

    public Entity getEastEngineer() {
        return eastEngineer;
    }

    public Entity getNorthEngineer() {
        return northEngineer;
    }

    public void setEastEngineer(Entity eastEngineer) {
        this.eastEngineer = eastEngineer;
    }

    public void setNorthEngineer(Entity northEngineer) {
        this.northEngineer = northEngineer;
    }

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        this.active = active;
    }

    double getDistance(Vec2Int a, Vec2Int b){ // Расстояние между точками
        return Math.hypot(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    Vec2Int goToNearestResource() {
        Entity entity = null;
        for (int d = 0; d < playerView.getMapSize(); d++) {
            for (int i = 0; i < d / 2 + 1; i++) {
                entity = entityById.get(filledCells[i][d - i]);
                if (entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(i, d - i);
                entity = entityById.get(filledCells[d - i][i]);
                if (entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(d - i, i);
            }
        }
        return new Vec2Int(playerView.getMapSize() / 2, playerView.getMapSize() / 2);
    }

    private Vec2Int getPlaceForTurretEast(int l, int r) {
        int x = LINE_OF_DEFENCE;
        int y = (l + r) / 2;
        if(isBuildPossible(x, y, 2)){
            return new Vec2Int(x, y);
        }
        if(r - l <= TURRET_MIN_RADIUS)
            return null;

        Vec2Int left = getPlaceForTurretEast(l, y);
        Vec2Int right = getPlaceForTurretEast(y, r);

        if(left != null)
            return left;
        if(right != null)
            return right;

        return null;
    }

    private Vec2Int getPlaceForTurretNorth(int l, int r) {
        int x = (l + r) / 2;
        int y = LINE_OF_DEFENCE;
        if(isBuildPossible(x, y, 2)){
            return new Vec2Int(x, y);
        }
        if(r - l <= TURRET_MIN_RADIUS)
            return null;

        Vec2Int left = getPlaceForTurretNorth(l, x);
        Vec2Int right = getPlaceForTurretNorth(x, r);

        if(left != null)
            return left;
        if(right != null)
            return right;

        return null;
    }

    boolean isBuildPossible(int x, int y, int size){ // Возможно ли строительство здания
        //Проверка на возможность подойти к месту строительства
        if(y == 0 || filledCells[x][y - 1] != 0)
            return false;
        //Проверка на чистоту площади постройки
        for(int xx = x; xx < x + size; xx++){
            for(int yy = y; yy < y + size; yy++){
                if(filledCells[xx][yy] != 0)
                    return false;
            }
        }
        //Проверка на чистоту у стены слева
        if(x > 0 && size > 1) {
            for (int yy = y; yy < y + size; yy++) {
                if (filledCells[x - 1][yy] != 0)
                    return false;
            }
        }
        else{
            return false;
        }

        return true;
    }

    Vec2Int getPlaceForWallEast(){
        for(var turret : turrets){
            if(turret.getPosition().getX() > 24){
                for(int i = turret.getPosition().getY() + 1; i >= turret.getPosition().getY();i--){
                    if(isBuildPossible(turret.getPosition().getX() + 2, i, 1)){
                        return new Vec2Int(turret.getPosition().getX() + 2, i);
                    }
                }
            }
        }
        return null;
    }

    Vec2Int getPlaceForWallNorth(){
        for(var turret : turrets){
            if(turret.getPosition().getY() > 24){
                for(int i = turret.getPosition().getY() + 1; i >= turret.getPosition().getY();i--){
                    if(isBuildPossible(i, turret.getPosition().getX() + 2, 1)){
                        return new Vec2Int(i, turret.getPosition().getX() + 2);
                    }
                }
            }
        }
        return null;
    }

    HashMap<Integer, Double> getPlayersPower(){ // Оценка сил армий противников без турелей
        HashMap<Integer, Double> result = new HashMap<>();
        ArrayList<Entity> list = new ArrayList<>();
        list.addAll(enemyEntities);

        for(var player : playerView.getPlayers())
            result.put(player.getId(), 0.0);

        for(var entity : list){
            if(entity.getPlayerId() == playerView.getMyId())
                continue;
            if(entity.getEntityType() == EntityType.MELEE_UNIT && (entity.getPlayerId() == playerView.getMyId() ||
                    enemyAtBase(entity)))
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + MEELE_POWER);
            else if(entity.getEntityType() == EntityType.RANGED_UNIT && (entity.getPlayerId() == playerView.getMyId() ||
                    enemyAtBase(entity)))
                result.put(entity.getPlayerId(), result.get(entity.getPlayerId()) + RANGE_POWER);
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

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }

    public void activate(PlayerView playerView, Player me, HashMap<Integer, Entity> entityById, int[][] filledCells,
                         ArrayList<Entity> turrets, ArrayList<Entity> enemyEntities,
                         HashMap<Integer, Integer> enemyPositions){
        this.playerView = playerView;
        this.me = me;
        this.entityById = entityById;
        this.filledCells = filledCells;
        this.turrets = turrets;
        this.enemyEntities = enemyEntities;
        this.enemyPositions = enemyPositions;
        this.playersPower = getPlayersPower();
        this.result = new HashMap<>();

        MoveAction moveActionEast = null;
        BuildAction buildActionEast = null;
        AttackAction attackActionEast = null;
        RepairAction repairActionEast = null;

        MoveAction moveActionNorth = null;
        BuildAction buildActionNorth = null;
        AttackAction attackActionNorth = null;
        RepairAction repairActionNorth = null;


        if(eastEngineer == null && northEngineer == null){
            active = 2;
            return;
        }

        //Определение приоритета строительства
        /*boolean turretEastPriority = false;
        boolean turretNorthPriority = false;
        int eastPlayerId = enemyPositions.get(1);
        int northPlayerId = enemyPositions.get(2);*/

        /*for(var player : playerView.getPlayers()){
            if(enemyPositions.get(player.getId()) == 1){
                eastPlayerId = player.getId();
            }
            if(enemyPositions.get(player.getId()) == 2){
                northPlayerId = player.getId();
            }
        }*/
        /*if(eastPlayerId != -1 && playersPower.get(eastPlayerId) > playersPower.get(northPlayerId))
            turretEastPriority = true;
        else if(northPlayerId != -1 && playersPower.get(northPlayerId) > playersPower.get(eastPlayerId))
            turretNorthPriority = true;*/

        //Подсчёт кол-ва турелей
        int turretsEastCount = 0;
        int turretsNorthCount = 0;
        for(var turret : turrets){
            if(turret.getPosition().getX() == LINE_OF_DEFENCE){
                turretsEastCount++;
            }
            if(turret.getPosition().getY() == LINE_OF_DEFENCE){
                turretsNorthCount++;
            }

        }

        if(eastEngineer != null)
        {
            System.out.println("EAST: " + eastEngineer.getPosition().getX() + " " + eastEngineer.getPosition().getY());
            System.out.println("ID: " + eastEngineer.getId());
        }
        if(northEngineer != null)
        {
            System.out.println("NORTH: " + northEngineer.getPosition().getX() + " " + northEngineer.getPosition().getY());
            System.out.println("ID: " + northEngineer.getId());
        }

        if(playerView.getCurrentTick() < TIME_TO_BUILD || me.getResource() < 100){
            var properties = playerView.getEntityProperties().get(eastEngineer.getEntityType()); // Свойства
            moveActionEast = moveActionNorth = new MoveAction(goToNearestResource(), true, true);

            attackActionEast = attackActionNorth = new AttackAction(
                    null,
                    new AutoAttack(
                            properties.getSightRange(),
                            new EntityType[]{EntityType.RESOURCE}
                    )
            );
        }
        else{
            Vec2Int placeForTurretEast = getPlaceForTurretEast(0, LINE_OF_DEFENCE);
            Vec2Int placeForWallEast = null;//getPlaceForWallEast();
            if(placeForTurretEast != null && /*!turretNorthPriority*/ turretsEastCount <= turretsNorthCount &&
            me.getResource() >= 100) {
                System.out.println("EAST: Gotta build the turret at " + placeForTurretEast.getX() + " " + placeForTurretEast.getY());

                moveActionEast = new MoveAction(
                        new Vec2Int(placeForTurretEast.getX(), placeForTurretEast.getY() - 1),
                        true,
                        false
                );
                buildActionEast = new BuildAction(
                        EntityType.TURRET,
                        placeForTurretEast
                );
                attackActionEast = null;
            }
            else if(placeForWallEast != null){
                System.out.println("EAST: Gotta build the wall at " + placeForWallEast.getX() + " " + placeForWallEast.getY());

                moveActionEast = new MoveAction(
                        new Vec2Int(placeForWallEast.getX(), placeForWallEast.getY() - 1),
                        true,
                        false
                );
                buildActionEast = new BuildAction(
                        EntityType.TURRET,
                        placeForWallEast
                );
                attackActionEast = null;
            }
            else {
                moveActionEast = new MoveAction(
                        new Vec2Int(LINE_OF_DEFENCE - 5, LINE_OF_DEFENCE / 2),
                        true,
                        false
                );
            }


            Vec2Int placeForTurretNorth = getPlaceForTurretNorth(0, LINE_OF_DEFENCE);
            Vec2Int placeForWallNorth = null;//getPlaceForWallNorth();
            if(placeForTurretNorth != null && /*!turretEastPriority*/turretsEastCount >= turretsNorthCount &&
                    me.getResource() >= 100) {
                System.out.println("NORTH: Gotta build the turret at " + placeForTurretNorth.getX() + " " + placeForTurretNorth.getY());

                moveActionNorth = new MoveAction(
                        new Vec2Int(placeForTurretNorth.getX(), placeForTurretNorth.getY() - 1),
                        true,
                        false
                );
                buildActionNorth = new BuildAction(
                        EntityType.TURRET,
                        placeForTurretNorth
                );


                attackActionNorth = null;
            }
            else if(placeForWallNorth != null){ //!!!
                System.out.println("EAST: Gotta build the wall at " + placeForWallNorth.getX() + " " + placeForWallNorth.getY());

                moveActionEast = new MoveAction(
                        new Vec2Int(placeForWallNorth.getX(), placeForWallNorth.getY() - 1),
                        true,
                        false
                );
                buildActionEast = new BuildAction(
                        EntityType.TURRET,
                        placeForWallNorth
                );
                attackActionEast = null;
            }
            else{
                moveActionNorth = new MoveAction(
                        new Vec2Int(LINE_OF_DEFENCE / 2, LINE_OF_DEFENCE - 5),
                        true,
                        false
                );
            }
        }

        if(eastEngineer != null)
            result.put(
                    eastEngineer.getId(),
                    new EntityAction(
                            moveActionEast,
                            buildActionEast,
                            attackActionEast,
                            repairActionEast
                    )
            );
        if(northEngineer != null)
        result.put(
                northEngineer.getId(),
                new EntityAction(
                        moveActionNorth,
                        buildActionNorth,
                        attackActionNorth,
                        repairActionNorth
                )
        );

        active = 2;
    }


}
