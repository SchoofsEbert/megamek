package megamek.common;

import junit.framework.TestCase;

public class EloScoreTest extends TestCase {
    private EloScore score;
    @Override
    public void setUp() throws Exception {
        score = new EloScore();
    }

    public void testDraw() {
        assertEquals(score.getTotalScore(), 0);

        score.draw(1000);
        assertEquals(score.getTotalScore(), 1000);

        score.draw(500);
        assertEquals(score.getTotalScore(), 750);
    }

    public void testWin() {
        assertEquals(score.getTotalScore(), 0);

        score.win(1000);
        assertEquals(score.getTotalScore(), 1400);

        score.win(1000);
        assertEquals(score.getTotalScore(), 1400);

    }

    public void testLose() {
        assertEquals(score.getTotalScore(), 0);

        score.win(1000);
        assertEquals(score.getTotalScore(), 1400);

        score.lose(1000);
        assertEquals(score.getTotalScore(), 1000);

        score.lose(500);
        assertEquals(score.getTotalScore(), 700);
    }
}