import model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Warlord /*extends Thread*/ { // Управляет только боевыми юнитами
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    PlayerView playerView;
    ArrayList<Entity> entities;
    HashMap<Integer, EntityAction> result;
    HashSet<Integer> aliveEnemies;
    HashMap<Integer, Integer> enemyPositions;

    EntityType[] targets = new EntityType[] {EntityType.MELEE_UNIT, EntityType.RANGED_UNIT, EntityType.MELEE_BASE,
            EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET, EntityType.WALL, EntityType.BUILDER_UNIT};
    EntityType[] turretTargets = new EntityType[] {EntityType.RANGED_UNIT, EntityType.MELEE_UNIT, EntityType.MELEE_BASE,
            EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.HOUSE, EntityType.TURRET, EntityType.WALL, EntityType.BUILDER_UNIT};

    public Warlord() {
    }

    /*public void activate(PlayerView playerView, ArrayList<Entity> warlordEntities, HashSet<Integer> aliveEnemies, HashMap<Integer, Integer> enemyPositions){
        this.playerView = playerView;
        this.entities = warlordEntities;
        this.aliveEnemies = aliveEnemies;
        this.enemyPositions = enemyPositions;
        active = true;
    }*/

    public void setActive(int active) {
        this.active = active;
    }

    public int getActive() {
        return active;
    }

    Vec2Int getAttackPoint(int position){
        if (position == 0){
            return new Vec2Int(
                    playerView.getMapSize() - 1,
                    playerView.getMapSize() - 1
            );
        }
        if (position == 1){
            return new Vec2Int(
                    playerView.getMapSize() - 1,
                    0
            );
        }
        if (position == 2){
            return new Vec2Int(
                    0,
                    playerView.getMapSize() - 1
            );
        }
        return new Vec2Int(
                playerView.getMapSize() - 1,
                playerView.getMapSize() - 1
        );
    }

    public HashMap<Integer, EntityAction> getResult() {
        return result;
    }

    public void activate(PlayerView playerView, ArrayList<Entity> warlordEntities, HashSet<Integer> aliveEnemies, HashMap<Integer, Integer> enemyPositions){
        System.out.println("WARLORD THREAD");
        this.playerView = playerView;
        this.entities = warlordEntities;
        this.aliveEnemies = aliveEnemies;
        this.enemyPositions = enemyPositions;

        result = new HashMap<>();
        var my_id = playerView.getMyId(); // Собственный Id
        for(var entity : entities){
            var properties = playerView.getEntityProperties().get(entity.getEntityType());

            MoveAction moveAction = null;
            if(aliveEnemies != null) {
                for (int position = 0; position < 3; position++) {
                    if (aliveEnemies.contains(enemyPositions.get(position)) == true) {
                        moveAction = new MoveAction(getAttackPoint(position), true, true);
                        //System.out.println("Attack " + position);
                        break;
                    }
                }
            }
            else {
                //System.out.println("Attack default");
                moveAction = new MoveAction(new Vec2Int(playerView.getMapSize() - 1, // Послать в другой конец карты
                        playerView.getMapSize() - 1), true, true);
            }
            BuildAction buildAction = null;
            AttackAction attackAction = new AttackAction(
                    null,
                    new AutoAttack(
                            properties.getSightRange(),
                            turretTargets
                    )
            );

            if(entity.getEntityType() == EntityType.TURRET) { // Турель
                moveAction = null;
                attackAction = new AttackAction(
                        null,
                        new AutoAttack(
                                properties.getSightRange(),
                                turretTargets
                        )
                );
            }

            result.put(
                    entity.getId(),
                    new EntityAction(
                            moveAction,
                            buildAction,
                            attackAction,
                            null
                    )
            );
        }
        active = 2;
        System.out.println("END OF WARLORD THREAD");
    }
}
