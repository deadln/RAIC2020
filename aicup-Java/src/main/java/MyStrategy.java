import model.*;

import java.util.*;

public class MyStrategy {
    Player me; // Объект самого игрока
    int builderChiefId = -1; // Прораб. Строит и ремонтирует здания
    Entity builderChief;

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
            for(var player : playerView.getPlayers()){
                if(player.getId() == playerView.getMyId()){
                    me = player;
                    break;
                }
            }
        }

        ArrayList<Entity> princeEntities = new ArrayList<>(); // Действия князя
        ArrayList<Entity> warlordEntities = new ArrayList<>(); // Действия воина

        int buildersCount = 0;
        int meleeCount = 0;
        int rangeCount = 0;

        Entity builderChiefCandidate = null;
        boolean builderChiefAlive = false;

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

            if(entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId()) // Пропуск вражеских и ресов
                continue;

            if(entity.getEntityType() == EntityType.MELEE_UNIT || entity.getEntityType() == EntityType.RANGED_UNIT) // Распределение управления над юнитами
                warlordEntities.add(entity);
            else
                princeEntities.add(entity);

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

        if(!builderChiefAlive) {
            builderChief = builderChiefCandidate;
            builderChiefId = builderChiefCandidate.getId();
        }


        Warlord warlord = new Warlord(playerView, warlordEntities);
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

    /*public class Warlord extends Thread { // Управляет только боевыми юнитами
        PlayerView playerView;
        ArrayList<Entity> entities;
        HashMap<Integer, EntityAction> result;

        public Warlord(PlayerView playerView, ArrayList<Entity> warlordEntities) {
            this.playerView = playerView;
            this.entities = warlordEntities;
        }

        public HashMap<Integer, EntityAction> getResult() {
            return result;
        }

        public void run(){
            result = new HashMap<>();
            var my_id = playerView.getMyId(); // Собственный Id
            for(var entity : entities){
                var properties = playerView.getEntityProperties().get(entity.getEntityType());

                MoveAction move_action = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                        playerView.getMapSize() - 1), true, true);
                BuildAction build_action = null;
                AttackAction attack_action = null;

                result.put(
                        entity.getId(),
                        new EntityAction(
                                move_action,
                                build_action,
                                new AttackAction(
                                        null,
                                        new AutoAttack(
                                                properties.getSightRange(),
                                                new EntityType[] {}
                                        )
                                ),
                                null
                        )
                );
            }
        }
    }

    public class Prince extends Thread {//Управляет только строителями
        PlayerView playerView;
        ArrayList<Entity> entities;
        HashMap<Integer, EntityAction> result;

        public Prince(PlayerView playerView, ArrayList<Entity> princeEntities) {
            this.playerView = playerView;
            this.entities = princeEntities;
        }

        public HashMap<Integer, EntityAction> getResult() {
            return result;
        }

        public void run(){
            result = new HashMap<>();
            var my_id = playerView.getMyId(); // Собственный Id
            for(var entity : entities){
                var properties = playerView.getEntityProperties().get(entity.getEntityType());

                MoveAction move_action = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                        playerView.getMapSize() - 1), true, true);
                BuildAction build_action = null;
                AttackAction attack_action = null;
                RepairAction repair_action = null;
                BuildProperties build_properties = null;

                if ((build_properties = properties.getBuild()) != null) { //Если сущность может в строительство
                    var entity_type = build_properties.getOptions()[0]; // Получить тип производимого юнита
                    var current_units = Arrays.stream(playerView.getEntities()).filter( // Посчитать текущее кол-во своих собственных юнитов конкретного типа
                            entity1 -> entity1.getPlayerId() != null &&
                                    entity1.getPlayerId() == my_id &&
                                    entity1.getEntityType() == entity_type).count();
                    //(Текущие кол-во данных юнитов + 1) * Сколько еды требует данный юнит
                    if ((current_units + 1) * playerView.getEntityProperties().get(entity_type).getPopulationUse() // Если есть возможность постройки юнита
                            <= properties.getPopulationProvide()) { // Сколько еды даёт текущее здание
                        build_action = new BuildAction( // Построить юнита
                                entity_type,
                                new Vec2Int(
                                        entity.getPosition().getX() + properties.getSize(),
                                        entity.getPosition().getY() + properties.getSize() - 1
                                )
                        );

                    }
                }

                result.put(
                        entity.getId(),
                        new EntityAction(
                                move_action,
                                build_action,
                                new AttackAction(
                                        null,
                                        new AutoAttack(
                                                properties.getSightRange(),
                                                new EntityType[] {EntityType.RESOURCE}
                                        )
                                ),
                                repair_action
                        )
                );
            }

        }
    }*/


}