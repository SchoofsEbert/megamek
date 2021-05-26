package megamek.common.score;

public interface IScore {
    int getTotalScore();

    void draw(int opponents);
    void win(int opponents);
    void lose(int opponents);
}
