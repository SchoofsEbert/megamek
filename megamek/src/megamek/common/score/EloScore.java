package megamek.common.score;

import java.io.Serializable;

public class EloScore implements IScore, Serializable {
    private static final long serialVersionUID = 2405172041950251807L;

    private int win_lose;
    private int games;
    private int opponents;

    public EloScore() {
        win_lose = 0;
        games = 0;
        opponents = 0;
    }

    public int getTotalScore() {
        if (games > 0) {
            return (opponents + 400 * win_lose) / games;
        }
        return 0;
    }

    private void game(int opponents) {
        games++;
        this.opponents += opponents;
    }
    public void draw(int opponents) {
        game(opponents);
    }

    public void win(int opponents) {
        game(opponents);
        win_lose++;
    }

    public void lose(int opponents) {
        game(opponents);
        win_lose--;
    }
}
