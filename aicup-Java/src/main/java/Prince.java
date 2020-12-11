import model.*;

import java.util.ArrayList;
import java.util.Comparator;
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
    //Entity builderChief;
    ArrayList<Entity> houseBuilders;
    HashSet<Integer> houseBuildersIds;
    int[][] filledCells;
    HashMap<Integer, Entity> entityById;
    HashMap<Integer, Integer> bases;

    double BUILDERS_RATIO = 0.5;
    double RANGED_RATIO = 0.6; //До TTF:  0.7 - 10851 , 0.5 - 10624
    int TIME_TO_FARM = 250; // После TTF: 0.7 146745, 0.5 - 140859
    int INAPPROPRIATE_PROVISION_REMAINING = 20;
    int RESOURCE_TO_BUILD = 50;
    int BASE_BOUNDS = 25;



    public Prince() {
        houseBuildersIds = new HashSet<>();
    }

    public void setHouseBuilders(ArrayList<Entity> houseBuilders) {
        this.houseBuilders = houseBuilders;
        this.houseBuilders.sort(new Comparator<Entity>() {
            @Override
            public int compare(Entity o1, Entity o2) {
                return o1.getId() - o2.getId();
            }
        });
        houseBuildersIds.clear();
        for(int i = 0; i < houseBuilders.size(); i++){
            houseBuildersIds.add(houseBuilders.get(i).getId());
        }
    } // log(2, 8) = 3

    public HashSet<Integer> getHouseBuildersIds() {
        return houseBuildersIds;
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
            //if(entity.getHealth() == properties.getMaxHealth()) // Учёт только построенных домов
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

    Vec2Int getPlaceForBase(){ // Найти место для базы
        for(int d = 1; d < playerView.getMapSize(); d++){//i % 5 == 1 && (d - i) % 5 == 1 &&
            for(int i = 0; i <= d; i++){
                if(isBuildPossible(i, d - i, 5))
                    return new Vec2Int(i, d - i);
                /*if(isBuildPossible(d - i, i, 3))
                    return new Vec2Int(d - i, i);*/
            }
        }
        return new Vec2Int(0,0);
    }

    ArrayList<Vec2Int> getPlacesForHouses(){ // Найти место для домов
        ArrayList<Vec2Int> res = new ArrayList<>();
        for(int x = 0; x < BASE_BOUNDS; x++){//i % 5 == 1 && (d - i) % 5 == 1 &&
            for(int y = 0; y < BASE_BOUNDS; y++){
                if(isBuildPossible(x, y, 3)){
                    res.add(new Vec2Int(x, y));
                    for(int i = x; i < x + 3; i++){
                        for(int j = y; j < y + 3; j++){
                            filledCells[i][j] = -1;
                        }
                    }
                    if(res.size() >= houseBuilders.size())
                        return res;
                }
                if(isBuildPossible(y, x, 3)){
                    res.add(new Vec2Int(y, x));
                    for(int i = y; i < y + 3; i++){
                        for(int j = x; j < x + 3; j++){
                            filledCells[i][j] = -1;
                        }
                    }
                    if(res.size() >= houseBuilders.size())
                        return res;
                }
                //return new Vec2Int(i, d - i);
                /*if(isBuildPossible(d - i, i, 3))
                    return new Vec2Int(d - i, i);*/
            }
        }
        //Сортировка в порядке близости друг к другу
        double distance, dis;
        for(int i = 0; i < res.size() - 1; i++){
            distance = getDistance(res.get(i), res.get(i + 1));
            for(int j = i + 1; j < res.size(); j++){
                dis = getDistance(res.get(i), res.get(j));
                if(dis < distance){
                    Vec2Int buff = res.get(i + 1);
                    res.set(i + 1, res.get(j));
                    res.set(j, buff);
                }
            }
        }

        return res;
    }

    /*ArrayList<Vec2Int> getPlacesForHouses(){ // Найти место для домов
        ArrayList<Vec2Int> res = new ArrayList<>();
        for(int d = 1; d < playerView.getMapSize(); d++){//i % 5 == 1 && (d - i) % 5 == 1 &&
            for(int i = 0; i <= d; i++){
                if(isBuildPossible(i, d - i, 3)){
                    res.add(new Vec2Int(i, d - i));
                    for(int x = i; x < i + 3; x++){
                        for(int y = d - i; y < d - i + 3; y++){
                            filledCells[x][y] = -1;
                        }
                    }
                    if(res.size() >= houseBuilders.size())
                        return res;
                }
                    //return new Vec2Int(i, d - i);
                /*if(isBuildPossible(d - i, i, 3))
                    return new Vec2Int(d - i, i);*/
//            }
//        }
//        return res;
//    }*/

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
                if(filledCells[entity.getPosition().getX() + properties.getSize()][y] == 0)
                    result.add(new Vec2Int(entity.getPosition().getX() + properties.getSize(), y));
            }
        }

        if(entity.getPosition().getY() < playerView.getMapSize() - 1){ // Верхняя сторона
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                if(filledCells[x][entity.getPosition().getY() + properties.getSize()] == 0)
                    result.add(new Vec2Int(x, entity.getPosition().getY() + properties.getSize()));
            }
        }

        /*result.sort(new Comparator<Vec2Int>() {
            @Override
            public int compare(Vec2Int o1, Vec2Int o2) {
                if(o1.getY() > o2.getY() || o1.getX() > o2.getX())
                    return -1;
                if(o1.getY() < o2.getY() || o1.getX() < o2.getX())
                    return 1;
                return 0;
            }
        });*/

        return result;
    }



    Vec2Int getNearestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points){
        double distance = (double) playerView.getMapSize(), dis;
        if(points.size() == 0)
            return new Vec2Int(15,15);
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
        var sides = getSides(entity);
        if(sides.size() == 0)
            return new Vec2Int(0,0);
        var best_place = sides.get(0);
        var coord = Math.max(best_place.getX(),best_place.getY());
        for(var place : sides){
            if(place.getX() * place.getX() + place.getY()*place.getY() > best_place.getX()*best_place.getX() +
                    best_place.getY()*best_place.getY()){
                best_place = place;
                coord = Math.max(best_place.getX(),best_place.getY());
            }
        }


        return best_place;

        /*if (playerView.getCurrentTick() % 2 == 0){
            return new Vec2Int(
                    entity.getPosition().getX() + size - 1,
                    entity.getPosition().getY() + size
            );
        }
        else{
            return new Vec2Int(
                    entity.getPosition().getX() + size,
                    entity.getPosition().getY() + size - 2
            );
        }*/
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

                Entity enemy = entityById.get(filledCells[i][j]);

                if(enemy == null || enemy.getPlayerId() == null || enemy.getPlayerId() == playerView.getMyId() ||
                        (enemy.getEntityType() != EntityType.RANGED_UNIT && enemy.getEntityType() != EntityType.MELEE_UNIT)){
                    continue;
                }
                double dis = getDistance(new Vec2Int(entity.getPosition().getX() + size - 1,
                        entity.getPosition().getY() + size - 1), enemy.getPosition());
                if(enemy != null && dis < distance){
                    nearestEnemy = enemy;
                    distance = dis;
                }
                //}
            }
        }

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
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE && getSides(entity).size() != 0)
                    return new Vec2Int(i, d - i);
                entity = entityById.get(filledCells[d - i][i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE && getSides(entity).size() != 0)
                    return new Vec2Int(d - i, i);
            }
        }

        for(int d = playerView.getMapSize() - 1; d >= 0; d--){
            for(int i = 0; i < d / 2 + 1; i++){
                entity = entityById.get(filledCells[i][d - i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE && getSides(entity).size() != 0)
                    return new Vec2Int(i, d - i);
                entity = entityById.get(filledCells[d - i][i]);
                if(entity != null && entity.getEntityType() == EntityType.RESOURCE && getSides(entity).size() != 0)
                    return new Vec2Int(d - i, i);
            }
        }

        return new Vec2Int(playerView.getMapSize(),playerView.getMapSize());
    }

    Entity getNearestRepairTarget(Entity entity, ArrayList<Entity> repairTargets){
        if(repairTargets.size() == 0)
            return null;
        double distance = playerView.getMapSize(), dis;
        Entity result = repairTargets.get(0);
        for(var target : repairTargets){
            dis = getDistance(entity.getPosition(), target.getPosition());
            if(dis < distance){
                distance = dis;
                result = target;
            }
        }
        return result;
    }

    ArrayList<Entity> getNearestBuilders(Entity building, int count){ // Функция поиска ближайших count строителей
        var properties = playerView.getEntityProperties().get(building.getEntityType()); // Свойства
        ArrayList<Entity> res = new ArrayList<>();
        // Левый нижний угол
        int ld_x = Math.max(0, building.getPosition().getX() - 1);
        int ld_y = Math.max(0, building.getPosition().getY() - 1);
        // Правый верхний угол
        int ru_x = Math.min(playerView.getMapSize() - 1, building.getPosition().getX() + properties.getSize());
        int ru_y = Math.min(playerView.getMapSize() - 1, building.getPosition().getY() + properties.getSize());

        int steps = 0;
        while(res.size() < count && steps < 30){
            Entity candidate = null;
            //Проход по нижней границе
            for(int x = ld_x; x <= ru_x; x++){
                candidate = entityById.get(filledCells[x][ld_y]);
                if(candidate == null)
                    continue;
                if(candidate.getEntityType() == EntityType.BUILDER_UNIT && candidate.getPlayerId() == me.getId() &&
                !houseBuildersIds.contains(candidate.getId())){
                    res.add(candidate);
                }
            }
            //Проход по левой границе
            for(int y = ld_y; y <= ru_y; y++){
                candidate = entityById.get(filledCells[ld_x][y]);
                if(candidate == null)
                    continue;
                if(candidate.getEntityType() == EntityType.BUILDER_UNIT && candidate.getPlayerId() == me.getId() &&
                        !houseBuildersIds.contains(candidate.getId())){
                    res.add(candidate);
                }
            }
            //Проход по верхней границе
            for(int x = ld_x; x <= ru_x; x++){
                candidate = entityById.get(filledCells[x][ru_y]);
                if(candidate == null)
                    continue;
                if(candidate.getEntityType() == EntityType.BUILDER_UNIT && candidate.getPlayerId() == me.getId() &&
                        !houseBuildersIds.contains(candidate.getId())){
                    res.add(candidate);
                }
            }
            //Проход по правой границе
            for(int y = ld_y; y <= ru_y; y++){
                candidate = entityById.get(filledCells[ru_x][y]);
                if(candidate == null)
                    continue;
                if(candidate.getEntityType() == EntityType.BUILDER_UNIT && candidate.getPlayerId() == me.getId() &&
                        !houseBuildersIds.contains(candidate.getId())){
                    res.add(candidate);
                }
            }
            //Смещение границ квадрата поиска
            if(ld_x > 0)
                ld_x--;
            if(ld_y > 0)
                ld_y--;
            if(ru_x < playerView.getMapSize() - 1)
                ru_x++;
            if(ru_y < playerView.getMapSize() - 1)
                ru_y++;

            steps++;
        }

        while(res.size() > count){
            res.remove(count);
        }
        return res;
    }

    ArrayList<Vec2Int> getReverseVec2IntArray(ArrayList<Vec2Int> array){
        var res = new ArrayList<Vec2Int>();
        for(var vec : array){
            res.add(new Vec2Int(vec.getX(), vec.getY()));
        }
        return res;
    }


    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }



    public void activate(PlayerView playerView, Player player, int[][] filledCells, HashMap<Integer, Entity> entityById,
                         ArrayList<Entity> princeEntities, ArrayList<Entity> buildings, int buildersCount, int meleeCount,
                         int rangeCount, Entity builderChief, HashSet<Integer> maintenanceIds, boolean redAlert,
                         HashMap<Integer, Integer> bases){
        this.playerView = playerView;
        this.me = player;
        this.filledCells = filledCells;
        this.entityById = entityById;
        this.entities = princeEntities;
        this.buildings = buildings;
        this.buildersCount = buildersCount;
        this.meleeCount = meleeCount;
        this.rangeCount = rangeCount;
        //this.builderChief = builderChief;
        this.redAlert = redAlert;
        this.bases = bases;

        var provision = getProvisionSumm(playerView); // Текущая провизия

        if(me.getResource() >= 300){
            BUILDERS_RATIO = 0.35;
        }
        else{
            BUILDERS_RATIO = 0.5;
        }

        if(bases.get(EntityType.MELEE_BASE.tag) == 1 && bases.get(EntityType.RANGED_BASE.tag) == 0)
            RANGED_RATIO = 0;
        else if(bases.get(EntityType.MELEE_BASE.tag) == 0 && bases.get(EntityType.RANGED_BASE.tag) == 1)
            RANGED_RATIO = 1;
        else if(bases.get(EntityType.MELEE_BASE.tag) == 1 && bases.get(EntityType.RANGED_BASE.tag) > 1)
            RANGED_RATIO = ((double)bases.get(EntityType.RANGED_BASE.tag)) / ((double)bases.get(EntityType.RANGED_BASE.tag) +
                    (double)bases.get(EntityType.MELEE_BASE.tag));
        else
            RANGED_RATIO = 0.6;
        System.out.println(bases.get(EntityType.MELEE_BASE.tag));
        System.out.println(bases.get(EntityType.RANGED_BASE.tag));
        System.out.println("RATIO " + RANGED_RATIO);

        var placesForHouses = getReverseVec2IntArray(getPlacesForHouses());

        int houseCounter = 0;

        ArrayList<Entity> repairTargets = new ArrayList<>();
        for(var building : buildings){
            var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());

            if(building.getHealth() < propertiesBuilding.getMaxHealth()){
                repairTargets.add(building);
            }
        }

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



            if(houseBuildersIds.contains(entity.getId())/*entity.getId() == builderChief.getId()*/) { // Если юнит - строитель домов
                // Постройка базы строителей
                if(bases.get(EntityType.BUILDER_BASE.tag) == 0 && me.getResource() >= 500){
                    Vec2Int placeForBase = getPlaceForBase();
                    move_action = new MoveAction(
                            new Vec2Int(placeForBase.getX(),placeForBase.getY() - 1),
                            true,
                            false
                    );
                    build_action = new BuildAction(
                            EntityType.BUILDER_BASE,
                            placeForBase
                    );
                }
                // Постройка базы ближников
                else if(bases.get(EntityType.MELEE_BASE.tag) == 0 && me.getResource() >= 500){
                    Vec2Int placeForBase = getPlaceForBase();
                    move_action = new MoveAction(
                            new Vec2Int(placeForBase.getX(),placeForBase.getY() - 1),
                            true,
                            false
                    );
                    build_action = new BuildAction(
                            EntityType.MELEE_BASE,
                            placeForBase
                    );
                }
                // Постройка базы дальников
                else if(me.getResource() >= 500){
                    Vec2Int placeForBase = getPlaceForBase();
                    move_action = new MoveAction(
                            new Vec2Int(placeForBase.getX(),placeForBase.getY() - 1),
                            true,
                            false
                    );
                    build_action = new BuildAction(
                            EntityType.RANGED_BASE,
                            placeForBase
                    );
                }
                // Постройка дома
                else if(provision - (buildersCount + meleeCount + rangeCount) <= INAPPROPRIATE_PROVISION_REMAINING &&
                me.getResource() >= RESOURCE_TO_BUILD && houseCounter < placesForHouses.size()){
                    Vec2Int placeForHouse = placesForHouses.get(houseCounter);
                    houseCounter++;
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
                    if(me.getResource() > 200){
                        Vec2Int place = null;
                        if(playerView.getCurrentTick() % 4 == 0)
                            place = new Vec2Int(entity.getPosition().getX(), entity.getPosition().getY() + 1);
                        else if(playerView.getCurrentTick() % 4 == 1)
                            place = new Vec2Int(entity.getPosition().getX() + 1, entity.getPosition().getY());
                        else if(playerView.getCurrentTick() % 4 == 2)
                            place = new Vec2Int(entity.getPosition().getX(), entity.getPosition().getY() - 1);
                        else
                            place = new Vec2Int(entity.getPosition().getX() - 1, entity.getPosition().getY());
                        if(place.getX() < 0){
                            place.setX(0);
                        }
                        if(place.getY() < 0){
                            place.setY(0);
                        }
                        build_action = new BuildAction(
                                EntityType.HOUSE,
                                place
                        );
                    }
                    attack_action = null;

                }
                //Поиск здания для ремонта
                /*if (build_action == null){
                    System.out.println("Gotta repair");
                    /*for (var building : repairTargets) {
                        var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());
                        if (building.getHealth() < propertiesBuilding.getMaxHealth() &&
                                building.getEntityType() == EntityType.HOUSE || ) {
                            //System.out.println("Gotta repair the " + building.getEntityType());*/
                            /*Entity repairTarget = getNearestRepairTarget(entity, repairTargets);
                            if(repairTarget != null){
                                move_action = new MoveAction(
                                        getNearestPoint(entity.getPosition(), getSides(repairTarget)),
                                        true,
                                        true
                                );
                                repair_action = new RepairAction(
                                        repairTarget.getId()
                                );
                            }*/
                            /*break;
                        }
                    }*/
                    /*if(repair_action != null){
                        System.out.println("Repair???");
                    }
                }*/

                if(repair_action == null && build_action == null/* && playerView.getCurrentTick() < TIME_TO_FARM*/){
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
            }
            else if(!maintenanceIds.contains(entity.getId())){
                Entity menace = null;//getEnemyNearby(entity);
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
                    var nearestResource = goToNearestResource();
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

            if (entity.getEntityType() == EntityType.RANGED_BASE/* && (playerView.getCurrentTick() > TIME_TO_FARM ||
                    redAlert)*/)//&& (me.getResource() > 50 || redAlert)) // Строительство ближников
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

            if (entity.getEntityType() == EntityType.MELEE_BASE/* && (playerView.getCurrentTick() > TIME_TO_FARM ||
                    redAlert)*/)// && (me.getResource() > 50 || redAlert)) // Строительство ближников
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

        // Ремонт зданий ближайшими строителями
        for(var target : repairTargets){
            var repairMen = getNearestBuilders(target, 3);
            for(var builder : repairMen){
                result.remove(builder.getId());
                result.put(
                        builder.getId(),
                        new EntityAction(
                                new MoveAction(
                                        getNearestPoint(builder.getPosition(), getSides(target)),
                                        true,
                                        true
                                ),
                        null,
                        null,
                                new RepairAction(
                                        target.getId()
                                )
                        )
                );
            }
        }
        System.out.println("Prince is done");
        active = 2;
    }
}
