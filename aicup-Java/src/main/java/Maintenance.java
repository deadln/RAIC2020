import model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

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

    Vec2Int getFarthestPoint(Vec2Int entityPosition,ArrayList<Vec2Int> points){
        double distance = (double) playerView.getMapSize(), dis;
        if(points.size() == 0)
            return new Vec2Int(0,0);
        Vec2Int result = points.get(0);
        for (var point : points) {
            dis = Math.hypot(Math.abs(entityPosition.getX() - point.getX()), Math.abs(entityPosition.getY() - point.getY()));
            if(dis > distance && filledCells[point.getX()][point.getY()] == 0){
                distance = dis;
                result = point;
            }
        }
        return result;
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

        buildings.sort(new Comparator<Entity>() {
            @Override
            public int compare(Entity o1, Entity o2) {
                return o1.getId() - o2.getId();
            }
        });

        for(var building : buildings){
            var propertiesBuilding = playerView.getEntityProperties().get(building.getEntityType());
            if(building.getHealth() < propertiesBuilding.getMaxHealth()){
                for(var entity : maintenance){
                    result.put(
                            entity.getId(),
                            new EntityAction(
                                    new MoveAction(
                                            getFarthestPoint(entity.getPosition(),getSides(building)),
                                            true,
                                            true
                                    ),
                                    null,
                                    null,
                                    new RepairAction(
                                            building.getId()
                                    )
                            )
                    );
                }
                /*//System.out.println("Gotta repair the " + building.getEntityType());
                move_action = new MoveAction(
                        getNearestPoint(entity.getPosition(),getSides(building)),
                        true,
                        true
                );
                repair_action = new RepairAction(
                        building.getId()
                );*/
                break;
            }
        }
        if(result.size() == 0 && playerView.getCurrentTick() < TIME_TO_FARM){
            for(var entity : maintenance){
                var properties = playerView.getEntityProperties().get(entity.getEntityType()); // Свойства
                result.put(
                        entity.getId(),
                        new EntityAction(
                                new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                                        playerView.getMapSize() - 1), true, true),
                                null,
                                new AttackAction(
                                        null,
                                        new AutoAttack(
                                                properties.getSightRange(),
                                                new EntityType[] {EntityType.RESOURCE}
                                        )
                                ),
                                null
                                )
                        );

            }
        }
        else if(result.size() == 0){
            for(var entity : maintenance){
                var properties = playerView.getEntityProperties().get(entity.getEntityType()); // Свойства
                result.put(
                        entity.getId(),
                        new EntityAction(
                                new MoveAction(new Vec2Int(12, // Послать в другой конец карты
                                        12), true, true),
                                null,
                                null,
                                null
                        )
                );

            }
        }

        active = 2;
    }
}
