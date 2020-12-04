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

    HashMap<Integer, Integer> enemyPositions; // Словарь позиция-id
    HashSet<Integer> aliveEnemies; // id - статус
    int builderChiefId = -1; // Прораб. Строит и ремонтирует здания
    Entity builderChief;

    public MyStrategy() {
        //myExecutor = Executors.newCachedThreadPool();
        myExecutor = Executors.newFixedThreadPool(2);
        enemyPositions = new HashMap<>();
        warlord = new Warlord();
        prince = new Prince();
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
        System.out.println("---------------------------------------------------");
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

        int buildersCount = 0; // Кол-во строителей
        int meleeCount = 0; // Кол-во милишников
        int rangeCount = 0; // Кол-во ренжей

        Entity builderChiefCandidate = null; // Строитель домов
        boolean builderChiefAlive = false;

        aliveEnemies = new HashSet<>();

        //Поиск занятых клеток
        HashSet<Pair> filledCells = new HashSet<>(); // ПЕРЕРАБОТАТЬ

        for(var entity: playerView.getEntities()){ // Проход по сущностям
            if(entity.getId() == 0){
                System.out.println("ID 0: " + entity.getEntityType() + " " + entity.getPosition().getX() + " " + entity.getPosition().getY());
            }

            var properties = playerView.getEntityProperties().get(entity.getEntityType());
            // Поиск занятых клеток ПЕРЕРАБОТАТЬ
            for(int x = entity.getPosition().getX(); x < entity.getPosition().getX() + properties.getSize(); x++){
                for(int y = entity.getPosition().getY(); y < entity.getPosition().getY() + properties.getSize(); y++){
                    filledCells.add(new Pair(x,y));
                }
            }

            if(entity.getPlayerId() != null && entity.getPlayerId() != playerView.getMyId()){
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

            //Подсчёт кол-ва юнитов
            if(entity.getEntityType() == EntityType.BUILDER_UNIT) {
                buildersCount++;
                if(entity.getId() == builderChiefId) // Проверка жив ли прораб
                    builderChiefAlive = true;
                else
                    builderChiefCandidate = entity;
            }
            if(entity.getEntityType() == EntityType.MELEE_UNIT)
                meleeCount++;
            if(entity.getEntityType() == EntityType.RANGED_UNIT)
                rangeCount++;


        }

        if(!builderChiefAlive && builderChiefCandidate != null) {
            builderChief = builderChiefCandidate;
            builderChiefId = builderChiefCandidate.getId();
        }


        //Warlord warlord = new Warlord(playerView, warlordEntities, aliveEnemies, enemyPositions);
        myExecutor.execute(new Runnable() {
            public void run() {
                warlord.setActive(1);
                warlord.activate(playerView, warlordEntities, aliveEnemies, enemyPositions);
            }
        });
        int finalBuildersCount = buildersCount;
        int finalMeleeCount = meleeCount;
        int finalRangeCount = rangeCount;
        myExecutor.execute(new Runnable() {
            @Override
            public void run() {
                prince.setActive(1);
                prince.activate(playerView, me, filledCells, princeEntities, finalBuildersCount, finalMeleeCount, finalRangeCount, builderChief);
            }
        });



        while(warlord.getActive() != 2 || prince.getActive() != 2){System.out.println("DEBUG");}
        warlord.setActive(0);
        prince.setActive(0);

        System.out.println("Actions performed");

        ConcurrentHashMap actions = new ConcurrentHashMap();
        actions.putAll(warlord.getResult());
        actions.putAll(prince.getResult());


        var result = new Action(new HashMap<>()); // Список действий
        //result.getEntityActions().putAll(warlord.getResult());
        result.getEntityActions().putAll(actions);

        return result;
    }
    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }



}