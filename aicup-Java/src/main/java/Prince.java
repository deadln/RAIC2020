import model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Prince extends Thread {//Управляет только строителями
    volatile boolean active = false;

    PlayerView playerView;
    Player me;
    ArrayList<Entity> entities;
    HashMap<Integer, EntityAction> result;

    int buildersCount;
    int meleeCount;
    int rangeCount;
    Entity builderChief;
    HashSet<Pair> filledCells;

    double BUILDERS_RATIO;



    public Prince() {
    }

    public void activate(PlayerView playerView, Player player, HashSet<Pair> filledCells, ArrayList<Entity> princeEntities, int buildersCount,
                         int meleeCount, int rangeCount, Entity builderChief){
        this.playerView = playerView;
        this.me = player;
        this.filledCells = filledCells;
        this.entities = princeEntities;
        this.buildersCount = buildersCount;
        this.meleeCount = meleeCount;
        this.rangeCount = rangeCount;
        this.builderChief = builderChief;
        BUILDERS_RATIO = 0.45;
        active = true;
    }

    public boolean isActive(){
        return active;
    }

    int getProvisionSumm(PlayerView playerView) // Сумма всей популяции
    {
        int sum = 0;
        for(var entity : playerView.getEntities())
        {
            if(entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId())
                continue;
            var properties = playerView.getEntityProperties().get(entity.getEntityType());
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
        for(int d = 0; d < playerView.getMapSize(); d++){
            for(int i = 0; i <= d; i++){
                if(isBuildPossible(i, d - i, 3))
                    return new Vec2Int(i, d - i);
                if(isBuildPossible(d - i, i, 3))
                    return new Vec2Int(d - i, i);
            }
        }
        return new Vec2Int(0,0);
    }

    boolean isBuildPossible(int x, int y, int size){ // Возможно ли строительство здания
        //Проверка на возможность подойти к месту строительства
        if(y == 0 || filledCells.contains(new Pair(x, y - 1)))
            return false;
        //Проверка на чистоту площади постройки
        for(int xx = x; xx < x + size; xx++){
            for(int yy = y; yy < y + size; yy++){
                if(filledCells.contains(new Pair(xx, yy)))
                    return false;
            }
        }
        //Проверка на чистоту у стены слева
        for(int yy = y; yy < y + size; yy++)
        {
            if(filledCells.contains(new Pair(x - 1, yy)))
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
                if(!filledCells.contains(new Pair(entity.getPosition().getX() - 1, y)))
                    result.add(new Vec2Int(entity.getPosition().getX() - 1, y));
            }
        }

        if(entity.getPosition().getY() > 0){ // Нижняя сторона
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                if(!filledCells.contains(new Pair(x, entity.getPosition().getY() - 1)))
                    result.add(new Vec2Int(x, entity.getPosition().getY() - 1));
            }
        }

        if(entity.getPosition().getX() < playerView.getMapSize() - 1){ // Правая сторона
            for(int y = entity.getPosition().getY(); y < entity.getPosition().getY() + properties.getSize(); y++){
                if(!filledCells.contains(new Pair(entity.getPosition().getX() + 1, y)))
                    result.add(new Vec2Int(entity.getPosition().getX() + 1, y));
            }
        }

        if(entity.getPosition().getY() < playerView.getMapSize() - 1){ // Верхняя сторона
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                if(!filledCells.contains(new Pair(x, entity.getPosition().getY() + 1)))
                    result.add(new Vec2Int(x, entity.getPosition().getY() + 1));
            }
        }
        return result;
    }



    Vec2Int getNearestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points){
        double distance = (double) playerView.getMapSize(), dis;
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

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }



    public void run(){
        while(true){
            if(active){
                var provision = getProvisionSumm(playerView); // Текущая провизия
                if(me.getResource() >= 150){
                    BUILDERS_RATIO = 0.2;
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

                    if(entity.getId() == builderChief.getId()){ // Если юнит - прораб
                        //Поиск здания для ремонта
                        for(var building : entities){
                            var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());
                            if(!isUnit(building.getEntityType()) && building.getHealth() < propertiesBuilding.getMaxHealth()){
                                System.out.println("Gotta repair the " + building.getEntityType());
                                move_action = new MoveAction(
                                        getNearestPoint(entity.getPosition(),getSides(building)),
                                        true,
                                        true
                                );
                                repair_action = new RepairAction(
                                        building.getId()
                                );
                                break;
                            }
                        }

                        if(repair_action == null && provision - (buildersCount + meleeCount + rangeCount) <= 5){
                            Vec2Int placeForHouse = getPlaceForHouse();
                            move_action = new MoveAction(
                                    new Vec2Int(placeForHouse.getX(),placeForHouse.getY() - 1),
                                    false,
                                    false
                            );
                            build_action = new BuildAction(
                                    EntityType.HOUSE,
                                    getPlaceForHouse()
                            );
                            attack_action = null;

                        }
                        else if(repair_action == null){
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
                    else{
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


                    if (entity.getEntityType() == EntityType.BUILDER_BASE) // Строительство рабочих
                    {
                        build_properties = properties.getBuild();
                        var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                        if(buildersCount <= provision * BUILDERS_RATIO){
                            build_action = new BuildAction( // Построить юнита
                                    entity_type,
                                    new Vec2Int(
                                            entity.getPosition().getX() + properties.getSize(),
                                            entity.getPosition().getY() + properties.getSize() - 1
                                    )
                            );
                        }
                    }

                    if (entity.getEntityType() == EntityType.MELEE_BASE) // Строительство ближников
                    {
                        build_properties = properties.getBuild();
                        var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                        if(meleeCount <= rangeCount)
                            build_action = new BuildAction( // Построить юнита
                                    entity_type,
                                    new Vec2Int(
                                            entity.getPosition().getX() + properties.getSize(),
                                            entity.getPosition().getY() + properties.getSize() - 1
                                    )
                            );
                    }

                    if (entity.getEntityType() == EntityType.RANGED_BASE) // Строительство ближников
                    {
                        build_properties = properties.getBuild();
                        var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                        if(rangeCount <= meleeCount)
                            build_action = new BuildAction( // Построить юнита
                                    entity_type,
                                    new Vec2Int(
                                            entity.getPosition().getX() + properties.getSize(),
                                            entity.getPosition().getY() + properties.getSize() - 1
                                    )
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
                active = false;
            }
            else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }


    }
}
