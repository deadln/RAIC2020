import model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Prince{//Управляет только строителями
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    PlayerView playerView;
    Player me;
    ArrayList<Entity> entities;
    ArrayList<Entity> buildings;
    HashMap<Integer, EntityAction> result;
    boolean redAlert;

    int buildersCount;
    int meleeCount;
    int rangeCount;
    Entity builderChief;
    int[][] filledCells;
    HashMap<Integer, Entity> entityById;

    double BUILDERS_RATIO = 0.5;
    double RANGED_RATIO = 0.5; //До TTF:  0.7 - 10851 , 0.5 - 10624
    int TIME_TO_FARM = 250; // После TTF: 0.7 146745, 0.5 - 140859
    int INAPPROPRIATE_PROVISION_REMAINING = 9;



    public Prince() {
    }

    public void setActive(int active) {
        this.active = active;
    }

    public int getActive() {
        return active;
    }

    int getProvisionSumm(PlayerView playerView) // Сумма всей популяции
    {
        int sum = 0;
        for(var entity : buildings)
        {
            var properties = playerView.getEntityProperties().get(entity.getEntityType());
            if(entity.getHealth() == properties.getMaxHealth()) // Учёт только построенных домов
                sum += properties.getPopulationProvide();
        }
        return sum;
    }

    boolean isUnit(EntityType ent_type){ // Является ли юнитом
        return ent_type == EntityType.BUILDER_UNIT ||
                ent_type == EntityType.MELEE_UNIT ||
                ent_type == EntityType.RANGED_UNIT;
    }

    Vec2Int getPlaceForHouse(){ // Найти место для дома
        for(int d = 1; d < playerView.getMapSize(); d++){//i % 5 == 1 && (d - i) % 5 == 1 &&
            for(int i = 0; i <= d; i++){
                if(isBuildPossible(i, d - i, 3))
                    return new Vec2Int(i, d - i);
                /*if(isBuildPossible(d - i, i, 3))
                    return new Vec2Int(d - i, i);*/
            }
        }
        return new Vec2Int(0,0);
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
        if(x > 0) {
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

    //Список всех свободных соседних с сущностью клеток
    ArrayList<Vec2Int> getSides(Entity entity){
        var properties = playerView.getEntityProperties().get(entity.getEntityType()); // Свойства
        ArrayList<Vec2Int> result = new ArrayList<>();

        if(entity.getPosition().getX() > 0){ // Левая сторона
            for(int y = entity.getPosition().getY(); y < entity.getPosition().getY() + properties.getSize(); y++){
                if(filledCells[entity.getPosition().getX() - 1][y] == 0)
                    result.add(new Vec2Int(entity.getPosition().getX() - 1, y));
            }
        }

        if(entity.getPosition().getY() > 0){ // Нижняя сторона
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                if(filledCells[x][entity.getPosition().getY() - 1] == 0)
                    result.add(new Vec2Int(x, entity.getPosition().getY() - 1));
            }
        }

        if(entity.getPosition().getX() < playerView.getMapSize() - 1){ // Правая сторона
            for(int y = entity.getPosition().getY(); y < entity.getPosition().getY() + properties.getSize(); y++){
                if(filledCells[entity.getPosition().getX() + 1][y] == 0)
                    result.add(new Vec2Int(entity.getPosition().getX() + 1, y));
            }
        }

        if(entity.getPosition().getY() < playerView.getMapSize() - 1){ // Верхняя сторона
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                if(filledCells[x][entity.getPosition().getY() + 1] == 0)
                    result.add(new Vec2Int(x, entity.getPosition().getY() + 1));
            }
        }
        return result;
    }



    Vec2Int getNearestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points){
        double distance = (double) playerView.getMapSize(), dis;
        if(points.size() == 0)
            return new Vec2Int(0,0);
        Vec2Int result = points.get(0);
        for (var point : points) {
            dis = Math.hypot(Math.abs(entityPosition.getX() - point.getX()), Math.abs(entityPosition.getY() - point.getY()));
            if(dis < distance){
                distance = dis;
                result = point;
            }
        }
        return result;
    }

    Vec2Int getUnitBuildPosition(Entity entity, int size){
        if (playerView.getCurrentTick() % 2 == 0){
            return new Vec2Int(
                    entity.getPosition().getX() + size,
                    entity.getPosition().getY() + size - 1
            );
        }
        else{
            return new Vec2Int(
                    entity.getPosition().getX() + size,
                    entity.getPosition().getY() + size - 2
            );
        }
    }

    Entity getEnemyNearby(Entity entity){
        var size = playerView.getEntityProperties().get(entity.getEntityType()).getSize();
        Entity nearestEnemy = null;
        double distance = playerView.getMapSize();


        for(int i = entity.getPosition().getX() - 6 - 1;i < entity.getPosition().getX() + size + 6; i++){
            if(i < 0 || i >= playerView.getMapSize())
                continue;
            for(int j = entity.getPosition().getY() + size + 6 ;j > entity.getPosition().getY() - 6 - 1; j--){
                if(j < 0 || j >= playerView.getMapSize())
                    continue;
                //if(i*i + j*j <= RED_ALERT_RADIUS*RED_ALERT_RADIUS){
                    /*if(filledCells[i][j] != 0)
                        System.out.println(filledCells[i][j]);*/
                Entity enemy = entityById.get(filledCells[i][j]);

                if(enemy == null || enemy.getPlayerId() == null || enemy.getPlayerId() == playerView.getMyId() ||
                        (enemy.getEntityType() != EntityType.RANGED_UNIT && enemy.getEntityType() != EntityType.MELEE_UNIT)){
                    //System.out.println("Not enemy");
                    continue;}
                double dis = getDistance(new Vec2Int(entity.getPosition().getX() + size - 1,
                        entity.getPosition().getY() + size - 1), enemy.getPosition());
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

    double getDistance(Vec2Int a, Vec2Int b){ // Расстояние между точками
        return Math.hypot(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    MoveAction getEscapeRoute(Entity entity, Entity enemy)
    {
        int x = entity.getPosition().getX();
        int y = entity.getPosition().getY();

        if(enemy.getPosition().getX() < entity.getPosition().getX())
            x += 1;
        if(enemy.getPosition().getY() > entity.getPosition().getY())
            y -= 1;
        if(enemy.getPosition().getX() > entity.getPosition().getX())
            x -= 1;
        if(enemy.getPosition().getY() < entity.getPosition().getY())
            y += 1;

        if(x < 0){
            x = 0;
            y = getNearestEdge(y);
        }
        if(y < 0){
            y = 0;
            x = getNearestEdge(x);
        }
        if(x >= playerView.getMapSize()){
            x = playerView.getMapSize() - 1;
            y = getNearestEdge(y);
        }
        if(y >= playerView.getMapSize()){
            y = playerView.getMapSize() - 1;
            x = getNearestEdge(x);
        }

        return new MoveAction(
                new Vec2Int(x,y), true, true
        );
    }

    int getNearestEdge(int x){
        if(x < playerView.getMapSize()){
            return 0;
        }
        else
            return playerView.getMapSize() - 1;
    }

    Vec2Int goToNearestResource(){
        Entity entity = null;
        for(int d = 0; d < playerView.getMapSize(); d++){
            for(int i = 0; i < d / 2 + 1; i++){
                entity = entityById.get(filledCells[i][d - i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(i, d - i);
                entity = entityById.get(filledCells[d - i][i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(d - i, i);
            }
        }

        for(int d = playerView.getMapSize() - 1; d >= 0; d--){
            for(int i = 0; i < d / 2 + 1; i++){
                entity = entityById.get(filledCells[i][d - i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(i, d - i);
                entity = entityById.get(filledCells[d - i][i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE)
                    return new Vec2Int(d - i, i);
            }
        }

        return new Vec2Int(0,0);
    }

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }



    public void activate(PlayerView playerView, Player player, int[][] filledCells, HashMap<Integer, Entity> entityById,
                         ArrayList<Entity> princeEntities, ArrayList<Entity> buildings, int buildersCount, int meleeCount,
                         int rangeCount, Entity builderChief, HashSet<Integer> maintenanceIds, boolean redAlert){
        this.playerView = playerView;
        this.me = player;
        this.filledCells = filledCells;
        this.entityById = entityById;
        this.entities = princeEntities;
        this.buildings = buildings;
        this.buildersCount = buildersCount;
        this.meleeCount = meleeCount;
        this.rangeCount = rangeCount;
        this.builderChief = builderChief;
        this.redAlert = redAlert;

        var provision = getProvisionSumm(playerView); // Текущая провизия

        if(me.getResource() >= 150){
            BUILDERS_RATIO = 0.4;
        }
        else{
            BUILDERS_RATIO = 0.5;
        }

        /*if(playerView.getCurrentTick() < TIME_TO_FARM){
            RANGED_RATIO = 0.5;
        }*/

        result = new HashMap<>(); // Результат
        var my_id = playerView.getMyId(); // Собственный Id

        for(var entity : entities){

            var properties = playerView.getEntityProperties().get(entity.getEntityType()); // Свойства


            MoveAction move_action = null;
            BuildAction build_action = null;
            AttackAction attack_action = null;
            RepairAction repair_action = null;
            BuildProperties build_properties = null;

            if(entity.getEntityType() == EntityType.TURRET) // Пропуск управления турелью
                continue;

            if(entity.getId() == builderChief.getId()) { // Если юнит - прораб
                //Поиск здания для ремонта
                if (playerView.getCurrentTick() < TIME_TO_FARM){
                    for (var building : buildings) {
                        var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());
                        if (building.getHealth() < propertiesBuilding.getMaxHealth()) {
                            //System.out.println("Gotta repair the " + building.getEntityType());
                            move_action = new MoveAction(
                                    getNearestPoint(entity.getPosition(), getSides(building)),
                                    true,
                                    true
                            );
                            repair_action = new RepairAction(
                                    building.getId()
                            );
                            break;
                        }
                    }
            }
                // Постройка дома
                if(repair_action == null && provision - (buildersCount + meleeCount + rangeCount) <= INAPPROPRIATE_PROVISION_REMAINING){
                    Vec2Int placeForHouse = getPlaceForHouse();
                    System.out.println("Gotta build the house at " + placeForHouse.getX() + " " + placeForHouse.getY());

                    move_action = new MoveAction(
                            new Vec2Int(placeForHouse.getX(),placeForHouse.getY() - 1),
                            true,
                            false
                    );
                    build_action = new BuildAction(
                            EntityType.HOUSE,
                            placeForHouse
                    );
                    attack_action = null;

                }
                else if(repair_action == null && playerView.getCurrentTick() < TIME_TO_FARM){
                    move_action = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                            playerView.getMapSize() - 1), true, true);
                    attack_action = new AttackAction(
                            null,
                            new AutoAttack(
                                    properties.getSightRange(),
                                    new EntityType[] {EntityType.RESOURCE}
                            )
                    );
                }
                else{
                    move_action = new MoveAction(new Vec2Int(11, // Послать в другой конец карты
                            11), true, true);
                }
            }
            else if(!maintenanceIds.contains(entity.getId())){
                var menace = getEnemyNearby(entity);
                if(menace != null){ // Побег в случае угрозы поблизости
                    move_action = getEscapeRoute(entity, menace);
                    attack_action = null;
                }
                else { // Действия шахтёров
                    /*if (entity.getId() % 2 == 0)
                        move_action = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                                (int)(playerView.getMapSize() / 3.5)), true, true);
                    else
                        move_action = new MoveAction(new Vec2Int((int)(playerView.getMapSize() / 3.5), // Послать в другой конец карты
                                playerView.getMapSize() - 1), true, true);*/

                    move_action = new MoveAction(goToNearestResource(), true, true);

                    attack_action = new AttackAction(
                            null,
                            new AutoAttack(
                                    properties.getSightRange(),
                                    new EntityType[]{EntityType.RESOURCE}
                                    )
                        );
                }
            }


            if (entity.getEntityType() == EntityType.BUILDER_BASE) // Строительство рабочих
            {
                build_properties = properties.getBuild();
                var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                Vec2Int position = getUnitBuildPosition(entity, properties.getSize());
                if(buildersCount <= provision * BUILDERS_RATIO){
                    build_action = new BuildAction( // Построить юнита
                            entity_type,
                            position
                    );
                }
            }

            if (entity.getEntityType() == EntityType.RANGED_BASE)//&& (me.getResource() > 50 || redAlert)) // Строительство ближников
            {
                build_properties = properties.getBuild();
                var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                Vec2Int position = getUnitBuildPosition(entity, properties.getSize());
                if(rangeCount <= RANGED_RATIO * (rangeCount + meleeCount))
                    build_action = new BuildAction( // Построить юнита
                            entity_type,
                            position
                    );
            }

            if (entity.getEntityType() == EntityType.MELEE_BASE)// && (me.getResource() > 50 || redAlert)) // Строительство ближников
            {
                build_properties = properties.getBuild();
                var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                Vec2Int position = getUnitBuildPosition(entity, properties.getSize());
                if(meleeCount <= (1 - RANGED_RATIO) * (rangeCount + meleeCount))
                    build_action = new BuildAction( // Построить юнита
                            entity_type,
                            position
                    );
            }




            result.put(
                    entity.getId(),
                    new EntityAction(
                            move_action,
                            build_action,
                            attack_action,
                            repair_action
                    )
            );
        }
        active = 2;



    }
}
