import model.*;

import java.util.ArrayList;
import java.util.HashMap;

public class Warlord extends Thread { // Управляет только боевыми юнитами
    PlayerView playerView;
    ArrayList<Entity> entities;
    HashMap<Integer, EntityAction> result;

    EntityType[] targets = new EntityType[] {EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.MELEE_BASE,
            EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET, EntityType.WALL, EntityType.BUILDER_UNIT};

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

            MoveAction moveAction = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                    playerView.getMapSize() - 1), true, true);
            BuildAction buildAction = null;
            AttackAction attackAction = null;

            result.put(
                    entity.getId(),
                    new EntityAction(
                            moveAction,
                            buildAction,
                            new AttackAction(
                                    null,
                                    new AutoAttack(
                                            properties.getSightRange(),
                                            targets
                                    )
                            ),
                            null
                    )
            );
        }
    }
}
