import model.*;

import java.util.Arrays;

public class MyStrategy {
    boolean is_unit(EntityType ent_type){
        if (ent_type == EntityType.BUILDER_UNIT ||
                ent_type == EntityType.MELEE_UNIT ||
                ent_type == EntityType.RANGED_UNIT)
            return true;
        return false;
    }

    EntityType[] is_builder(EntityType ent_type){
        if (ent_type == EntityType.BUILDER_UNIT)
            return new EntityType[] {EntityType.RESOURCE};
        return new EntityType[] {};
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        var result = new Action(new java.util.HashMap<>());
        var my_id = playerView.getMyId();
        for(var entity: playerView.getEntities()){ // Цикл по всем собственным сущностям
            if(entity.getPlayerId() == null || entity.getPlayerId() != playerView.getMyId())
                continue;
            // Свойства сущности
            var properties = playerView.getEntityProperties().get(entity.getEntityType());

            MoveAction move_action = null;
            BuildAction build_action = null;
            BuildProperties build_properties = null;

            if(is_unit(entity.getEntityType())){ // Если это юнит
                move_action = new MoveAction(new Vec2Int(playerView.getMapSize() - 1,
                        playerView.getMapSize() - 1), true, true);
            }
            else if ((build_properties = properties.getBuild()) != null)  { //!!!
                var entity_type = build_properties.getOptions()[0];
                var current_units = Arrays.stream(playerView.getEntities()).filter(
                        entity1 -> entity1.getPlayerId() != null &&
                                entity1.getPlayerId() == my_id &&
                                entity1.getEntityType() == entity_type).count();
                if ((current_units + 1) * playerView.getEntityProperties().get(entity_type).getPopulationUse()
                        <= properties.getPopulationProvide()){
                    build_action = new BuildAction(
                            entity_type,
                            new Vec2Int(
                                    entity.getPosition().getX() + properties.getSize(),
                                    entity.getPosition().getY() + properties.getSize() - 1
                            )
                    );

                }
            }

            result.getEntityActions().put(
                    entity.getId(),
                    new EntityAction(
                            move_action,
                            build_action,
                            new AttackAction(
                                    null,
                                    new AutoAttack(
                                            properties.getSightRange(),
                                            is_builder(entity.getEntityType())
                                    )
                            ),
                            null
                    )
            );
        }
        return result;
    }
    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }
}