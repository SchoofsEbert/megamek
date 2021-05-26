package megamek.common;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.icons.Camouflage;
import megamek.common.logging.FakeLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.IConnection;
import megamek.server.GameServer;
import megamek.server.Server;
import megamek.server.victory.Victory;
import megamek.server.victory.VictoryResult;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.List;

public class GameLogicTest extends TestCase {
    Server server;
    GameLogic gamelogic;
    Game game;
    BipedMech entity;
    Player player;
    //MMLogger logger;

    @Override
    public void setUp() throws Exception {
        server = new Server("password", 8123);

        game = new Game();
        entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        game.addPlayer(0, player);
        game.addEntity(entity);

        //logger = Mockito.mock(FakeLogger.class);
        //MegaMek.setLogger(logger);

        gamelogic = new GameLogic(server);

        gamelogic.setGame(game);
    }

    @Override
    public void tearDown() throws Exception {
        server.die();
    }

    //Moved to GameLogic
    public void testSetGame() {
        assertEquals(entity.getGame(), game);
        assertTrue(player.isGhost());
        assertEquals(gamelogic.getGame(), game);
    }

    public void testAddNewPlayer() {
        gamelogic.addNewPlayer(1, "newplayer");

        IPlayer newplayer = gamelogic.getGame().getPlayer(1);

        assertEquals(PlayerColour.RED, newplayer.getColour());
        assertEquals(Camouflage.COLOUR_CAMOUFLAGE, newplayer.getCamoCategory());
        assertEquals(0, newplayer.getScore().getTotalScore());
        assertEquals("newplayer", newplayer.getName());
    }

    public void testCorrectDupeName() {
        String fixed_name = gamelogic.correctDupeName("JohnDoe");

        assertEquals("JohnDoe.2", fixed_name);
    }

    private void setVictoryResult(VictoryResult result) throws IllegalAccessException, NoSuchFieldException {
        Victory vic = Mockito.mock(Victory.class);
        Mockito.when(vic.checkForVictory(game, game.getVictoryContext())).thenReturn(result);
        Field victory = Game.class.getDeclaredField("victory");
        victory.setAccessible(true);
        victory.set(game, vic);
    }

    //Moved to GameLogic
    public void testVictoryFalse() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult result = Mockito.mock(VictoryResult.class);
        Mockito.when(result.victory()).thenReturn(false);

        setVictoryResult(result);

        assertFalse(gamelogic.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    //Moved to GameLogic
    public void testVictoryDraw() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult draw = Mockito.mock(VictoryResult.class);
        Mockito.when(draw.isDraw()).thenReturn(true);
        Mockito.when(draw.victory()).thenReturn(true);

        setVictoryResult(draw);

        assertTrue(gamelogic.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    public void testVictoryWon() throws NoSuchFieldException, IllegalAccessException {
        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.isDraw()).thenReturn(false);
        Mockito.when(won.victory()).thenReturn(true);
        Mockito.when(won.getWinningPlayer()).thenReturn(0);
        Mockito.when(won.getWinningTeam()).thenReturn(1);

        setVictoryResult(won);

        assertTrue(gamelogic.victory());
        assertEquals(game.getVictoryPlayerId(), 0);
        assertEquals(game.getVictoryTeam(), 1);
    }

    public void testUpdatePlayerScoresTeamWin() {
        game.setPhase(IGame.Phase.PHASE_VICTORY);
        EloScore score = new EloScore();
        score.win(4000);
        player.setScore(score);
        IPlayer player2 = new Player(1, "teammate");
        EloScore score2 = new EloScore();
        score2.win(4000);
        player2.setScore(score2);
        game.addPlayer(1, player2);
        IPlayer player3 = new Player(2, "Opponent");
        EloScore score3 = new EloScore();
        score3.win(4000);
        player3.setScore(score3);
        player3.setTeam(2);
        game.addPlayer(2, player3);

        game.setVictoryTeam(2);

        gamelogic.updatePlayerScores();

        assertEquals(6400, player.getScore().getTotalScore());
        assertEquals(6400, player2.getScore().getTotalScore());
        assertEquals(6800, player3.getScore().getTotalScore());
    }

    public void testUpdatePlayerScoresPlayerWin() {
        game.setPhase(IGame.Phase.PHASE_VICTORY);
        EloScore score = new EloScore();
        score.win(4000);
        player.setScore(score);
        IPlayer player2 = new Player(1, "teammate");
        EloScore score2 = new EloScore();
        score2.win(4000);
        player2.setScore(score2);
        game.addPlayer(1, player2);
        IPlayer player3 = new Player(2, "Opponent");
        EloScore score3 = new EloScore();
        score3.win(4000);
        player3.setScore(score3);
        game.addPlayer(2, player3);

        game.setVictoryPlayerId(0);

        gamelogic.updatePlayerScores();

        assertEquals(6800, player.getScore().getTotalScore());
        assertEquals(6400, player2.getScore().getTotalScore());
        assertEquals(6400, player3.getScore().getTotalScore());
    }

    private void endCurrentPhase(IGame.Phase phase) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        game.setPhase(phase);

        Method endcurrentphase = Server.class.getDeclaredMethod("endCurrentPhase");
        endcurrentphase.setAccessible(true);
        endcurrentphase.invoke(server);
    }

    public void testGetGhostConnIdByNameExisting() {
        int id = gamelogic.getGhostIdByName("JohnDoe");

        assertEquals(0, id);
    }

    public void testGetGhostConnIdByNameNonExisting() {
        int id = gamelogic.getGhostIdByName("IDontExist");

        assertEquals(-1, id);
    }
}
