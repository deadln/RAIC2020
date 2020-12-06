import model.*;

import java.util.*;

public class Maintenance {
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    HashSet<Integer> maintenanceIds;
    ArrayList<Entity> maintenance;
    private PlayerView playerView;
    Player me;

    HashMap<Integer, EntityAction> result;
    int[][] filledCells;

    int TIME_TO_FARM = 250;

    public Maintenance() {
        maintenanceIds = new HashSet<>();
    }

    public HashSet<Integer> getMaintenanceIds() {
        return maintenanceIds;
    }

    public void setMaintenance(ArrayList<Entity> maintenance) {
        this.maintenance = maintenance;
        maintenanceIds.clear();
        for(int i = 0; i < maintenance.size(); i++){
            maintenanceIds.add(maintenance.get(i).getId());
        }
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

    Vec2Int getNearestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points, int size){ // Самая дальняя точка
        double distance = (double) playerView.getMapSize(), dis;
        if(points.size() == 0)
            return new Vec2Int(0,0);
        Vec2Int result = getAnyPoint(points);
        int far_y = playerView.getMapSize();
        for (var point : points) {
            dis = Math.hypot(Math.abs(entityPosition.getX() - point.getX()), Math.abs(entityPosition.getY() - point.getY()));
            if(filledCells[point.getX()][point.getY()] == 0 && (point.getY() > far_y || dis < distance)){
                far_y = point.getY();
                distance = dis;
                result = point;
            }
        }
        return result;
    }

    Vec2Int getFarthestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points, int size){ // Самая дальняя точка
        double distance = (double) playerView.getMapSize(), dis;
        if(points.size() == 0)
            return new Vec2Int(0,0);
        Vec2Int result = getAnyPoint(points);
        int far_y = playerView.getMapSize();
        for (var point : points) {
            dis = Math.hypot(Math.abs(entityPosition.getX() - point.getX()), Math.abs(entityPosition.getY() - point.getY()));
            if(filledCells[point.getX()][point.getY()] == 0 && (point.getY() > far_y || dis > distance)){
                far_y = point.getY();
                distance = dis;
                result = point;
            }
        }
        return result;
    }

    Vec2Int getAnyPoint(ArrayList<Vec2Int> points){ // Любая точка
        if(points.size() == 0)
            return new Vec2Int(0,0);
        return points.get((int) (Math.random() * points.size()));
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


    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }

    public void activate(PlayerView playerView, Player me, int[][] filledCells, ArrayList<Entity> buildings) {
        this.playerView = playerView;
        this.me = me;
        this.filledCells = filledCells;
        active = 1;
        result = new HashMap<>();

        /*buildings.sort(new Comparator<Entity>() {
            @Override
            public int compare(Entity o1, Entity o2) {
                return o1.getId() - o2.getId();
            }
        });*/

        ArrayList<Entity> targets = new ArrayList<>();
        for(var building : buildings){
            var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());

            if(building.getHealth() < propertiesBuilding.getMaxHealth()){
                targets.add(building);
            }
        }

        Entity target = null;
        double distance = playerView.getMapSize(), dis;

        for(var entity : maintenance){ // Проход по ремонтникам
            MoveAction moveAction = null;
            RepairAction repairAction = null;
            AttackAction attackAction = null;

            target = null;
            distance = playerView.getMapSize();
            for(var building : targets){ // Поиск ближайшего здания к юниту
                dis = getDistance(entity.getPosition(), building.getPosition());
                if(dis < distance){
                    distance = dis;
                    target = building;
                }
            }
            if(target != null){
                var targetProperties = playerView.getEntityProperties().get(target.getEntityType());
                var fp = getFarthestPoint(entity.getPosition(),getSides(target), targetProperties.getSize());
                moveAction = new MoveAction(
                        fp,
                        true,
                        true
                );
                repairAction = new RepairAction(
                        target.getId()
                );
            }
            else { // Если нечего ремонтировать
                moveAction = new MoveAction(new Vec2Int(4, // Послать в другой конец карты
                        4), true, true);
                if(playerView.getCurrentTick() < TIME_TO_FARM){
                    var properties = playerView.getEntityProperties().get(entity.getEntityType());
                    moveAction = new MoveAction(new Vec2Int(playerView.getMapSize(), playerView.getMapSize()),
                            true, true);
                    attackAction = new AttackAction(
                            null,
                            new AutoAttack(
                                    properties.getSightRange(),
                                    new EntityType[] {EntityType.RESOURCE}
                            )
                    );
                }
                repairAction = null;
            }

            result.put(
                    entity.getId(),
                    new EntityAction(
                            moveAction,
                            null,
                            attackAction,
                            repairAction
                    )
            );
        }

        active = 2;
    }
}
