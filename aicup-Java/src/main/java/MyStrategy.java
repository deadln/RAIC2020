import com.sun.source.tree.SynchronizedTree;
import model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyStrategy {
    ExecutorService myExecutor;

    Player me; // Объект самого игрока
    Warlord warlord;
    Prince prince;
    Maintenance maintenance;

    HashMap<Integer, Entity> entityById;
    HashMap<Integer, Integer> enemyPositions; // Словарь позиция-id
    HashSet<Integer> aliveEnemies; // id - статус
    int builderChiefId = -1; // Прораб. Строит и ремонтирует здания
    Entity builderChief;

    //boolean[][] dfs_field; // TODO Определение карты с кучей ресов в центре

    int MAINTENANCE_MAX_COUNT = 3;

    public MyStrategy() {
        //myExecutor = Executors.newCachedThreadPool();
        myExecutor = Executors.newFixedThreadPool(4);
        enemyPositions = new HashMap<>();
        warlord = new Warlord();
        prince = new Prince();
        maintenance = new Maintenance();

        entityById = new HashMap<>();
    }

    public ExecutorService getMyExecutor() {
        return myExecutor;
    }

    boolean isUnit(EntityType ent_type){ // Является ли юнитом
        if (ent_type == EntityType.BUILDER_UNIT ||
                ent_type == EntityType.MELEE_UNIT ||
                ent_type == EntityType.RANGED_UNIT)
            return true;
        return false;
    }

    EntityType[] getAutoattackTarget(EntityType ent_type){ // Цель атаки
        if (ent_type == EntityType.BUILDER_UNIT)
            return new EntityType[] {EntityType.RESOURCE};
        return new EntityType[] {};
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

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        if(playerView.getCurrentTick() == 0){ // Стартовые действия
            enemyPositions = new HashMap<>();
        }

        // Найти своего игрока и врагов
        for(var player : playerView.getPlayers()){ //
            if(player.getId() == playerView.getMyId()){
                me = player;
            }

        }

        ArrayList<Entity> princeEntities = new ArrayList<>(); // Сущности князя
        ArrayList<Entity> warlordEntities = new ArrayList<>(); // Сущности воина
        ArrayList<Entity> maintenanceEntities = new ArrayList<>(); // Ремонтники
        ArrayList<Entity> maintenanceCandidates = new ArrayList<>(); // Кандидаты в ремонтники
        ArrayList<Entity> buildings = new ArrayList<>();
        ArrayList<Entity> enemyEntities = new ArrayList<>();


        int buildersCount = 0; // Кол-во строителей
        int meleeCount = 0; // Кол-во милишников
        int rangeCount = 0; // Кол-во ренжей

        Entity builderChiefCandidate = null; // Строитель домов
        boolean builderChiefAlive = false;

        aliveEnemies = new HashSet<>();

        //Поиск занятых клеток
        //HashSet<Pair> filledCells = new HashSet<>(); // ПЕРЕРАБОТАТЬ
        int[][] filledCells = new int[playerView.getMapSize()][playerView.getMapSize()]; // Поле с Id сущностей, которые занимают клетки

        int provision = getProvisionSumm(playerView);

        for(var entity: playerView.getEntities()){ // Проход по сущностям
            entityById.put(entity.getId(), entity);

            var properties = playerView.getEntityProperties().get(entity.getEntityType());

            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                for(int y = entity.getPosition().getY(); y < entity.getPosition().getY() + properties.getSize(); y++){
                    filledCells[x][y] = entity.getId();
                }
            }

            if(entity.getPlayerId() != null && entity.getPlayerId() != playerView.getMyId()){
                enemyEntities.add(entity);
                aliveEnemies.add(entity.getPlayerId());
                //Поиск стартовых позиций врагов
                if(playerView.getCurrentTick() == 0) {
                    //Справа сверху
                    if (entity.getPosition().getX() > playerView.getMapSize() / 2 && entity.getPosition().getY() > playerView.getMapSize() / 2) {
                        enemyPositions.put(0, entity.getPlayerId());
                    }
                    //Справа
                    if (entity.getPosition().getX() > playerView.getMapSize() / 2 && entity.getPosition().getY() < playerView.getMapSize() / 2) {
                        enemyPositions.put(1, entity.getPlayerId());
                    }
                    //Сверху
                    if (entity.getPosition().getX() < playerView.getMapSize() / 2 && entity.getPosition().getY() > playerView.getMapSize() / 2) {
                        enemyPositions.put(2, entity.getPlayerId());
                    }
                    for(int i = 0; i < 3; i++){
                        if(enemyPositions.get(i) == null){
                            enemyPositions.put(i, -1);
                        }
                    }
                }
            }



            if(entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) // Пропуск вражеских и ресов
                continue;

            if(entity.getEntityType() == EntityType.TURRET){
                warlordEntities.add(entity);
                princeEntities.add(entity);
            }
            else if(entity.getEntityType() == EntityType.MELEE_UNIT || entity.getEntityType() == EntityType.RANGED_UNIT) { // Распределение управления над юнитами
                warlordEntities.add(entity);
            }
            else {
                princeEntities.add(entity);
            }

            if(!isUnit(entity.getEntityType())){
                buildings.add(entity);
            }

            //Подсчёт кол-ва юнитов
            if(entity.getEntityType() == EntityType.BUILDER_UNIT) {
                buildersCount++;
                if(entity.getId() == builderChiefId) // Проверка жив ли прораб
                    builderChiefAlive = true;
                if(!builderChiefAlive)
                    builderChiefCandidate = entity;
                if(entity.getId() != builderChiefId){
                    if(maintenance.getMaintenanceIds().contains(entity.getId()))
                        maintenanceEntities.add(entity);
                    else
                        maintenanceCandidates.add(entity);
                }
            }
            if(entity.getEntityType() == EntityType.MELEE_UNIT)
                meleeCount++;
            if(entity.getEntityType() == EntityType.RANGED_UNIT)
                rangeCount++;


        }
        //Назначение нового прораба
        if(!builderChiefAlive && builderChiefCandidate != null) {
            builderChief = builderChiefCandidate;
            builderChiefId = builderChiefCandidate.getId();
        }

        //Назначение рембригады
        int i = 0;
        while(maintenanceEntities.size() < MAINTENANCE_MAX_COUNT + (provision - 30) / 15 && i < maintenanceCandidates.size()){
            maintenanceEntities.add(maintenanceCandidates.get(i));
            i++;
        }
        maintenance.setMaintenance(maintenanceEntities);

        //Определение

        //Warlord warlord = new Warlord(playerView, warlordEntities, aliveEnemies, enemyPositions);
        myExecutor.execute(new Runnable() {
            public void run() {
                warlord.setActive(1);
                warlord.activate(playerView, warlordEntities, aliveEnemies, enemyPositions, buildings, filledCells,
                        entityById, enemyEntities);
            }
        });


        int finalBuildersCount = buildersCount;
        int finalMeleeCount = meleeCount;
        int finalRangeCount = rangeCount;
        myExecutor.execute(new Runnable() {
            @Override
            public void run() {
                prince.setActive(1);
                prince.activate(playerView, me, filledCells, entityById, princeEntities, new ArrayList<>(buildings), finalBuildersCount,
                        finalMeleeCount, finalRangeCount, builderChief, maintenance.getMaintenanceIds(), warlord.isRedAlert());
            }
        });

        myExecutor.execute(new Runnable() {
            public void run() {
                maintenance.setActive(1);
                maintenance.activate(playerView, me, filledCells, new ArrayList<>(buildings));
            }
        });



        while(warlord.getActive() != 2 || prince.getActive() != 2 || maintenance.getActive() != 2){;}
        warlord.setActive(0);
        prince.setActive(0);
        maintenance.setActive(0);


        var result = new Action(new HashMap<>()); // Список действий
        result.getEntityActions().putAll(warlord.getResult());
        result.getEntityActions().putAll(prince.getResult());
        result.getEntityActions().putAll(maintenance.getResult());

        return result;
    }
    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }



}