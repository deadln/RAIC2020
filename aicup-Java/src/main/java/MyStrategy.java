import model.*;

import java.util.*;//2|0
//                   P|1
public class MyStrategy {
    Player me; // Объект самого игрока
    HashMap<Integer, Integer> enemyPositions; // Словарь позиция-id
    HashSet<Integer> aliveEnemies; // id - статус
    int builderChiefId = -1; // Прораб. Строит и ремонтирует здания
    Entity builderChief;

    public MyStrategy() {
        enemyPositions = new HashMap<>();
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
            // Найти своего игрока и врагов
            for(var player : playerView.getPlayers()){ //
                if(player.getId() == playerView.getMyId()){
                    me = player;
                }
            }

        }

        for(var player : playerView.getPlayers()){ //
            if(player.getId() == playerView.getMyId()){
                me = player;
            }

        }

        ArrayList<Entity> princeEntities = new ArrayList<>(); // Действия князя
        ArrayList<Entity> warlordEntities = new ArrayList<>(); // Действия воина

        int buildersCount = 0;
        int meleeCount = 0;
        int rangeCount = 0;

        Entity builderChiefCandidate = null;
        boolean builderChiefAlive = false;

        aliveEnemies = new HashSet<>();

        //Поиск занятых клеток
        HashSet<Pair> filledCells = new HashSet<>();

        for(var entity: playerView.getEntities()){ // Проход по сущностям

            var properties = playerView.getEntityProperties().get(entity.getEntityType());
            // Поиск занятых клеток
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


        Warlord warlord = new Warlord(playerView, warlordEntities, aliveEnemies, enemyPositions);
        Prince prince = new Prince(playerView, me, filledCells, princeEntities, buildersCount, meleeCount, rangeCount, builderChief);
        warlord.start();
        prince.start();


        while(warlord.isAlive() || prince.isAlive()){;}

        var result = new Action(new HashMap<>()); // Список действий
        result.getEntityActions().putAll(warlord.getResult());
        result.getEntityActions().putAll(prince.getResult());

        return result;
    }
    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }




}