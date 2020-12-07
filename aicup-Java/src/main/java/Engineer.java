import model.*;

public class Engineer {
    volatile int active = 0; // Статус активности. 0 - не активен. 1 - активен. 2 - завершил работу

    Entity eastEngineer;
    Entity northEngineer;

    PlayerView playerView;

    public Engineer() {
    }

    public Entity getEastEngineer() {
        return eastEngineer;
    }

    public Entity getNorthEngineer() {
        return northEngineer;
    }

    public void setEastEngineer(Entity eastEngineer) {
        this.eastEngineer = eastEngineer;
    }

    public void setNorthEngineer(Entity northEngineer) {
        this.northEngineer = northEngineer;
    }

    public int getActive() {
        return active;
    }

    public void setActive(int active) {
        this.active = active;
    }

    public void activate(PlayerView playerView){
        this.playerView = playerView;

        active = 2;
    }
}
