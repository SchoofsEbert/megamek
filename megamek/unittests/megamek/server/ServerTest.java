package megamek.server;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.client.ui.swing.boardview.FieldofFireSprite;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.*;
import megamek.common.icons.Camouflage;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.logging.FakeLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.ConnectionFactory;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import megamek.common.options.GameOptions;
import megamek.common.weapons.ACAPHandler;
import megamek.common.weapons.AttackHandler;
import megamek.server.victory.Victory;
import megamek.server.victory.VictoryResult;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerTest extends TestCase {
    Server server;
    Game game;
    BipedMech entity;
    Player player;
    MMLogger logger;

    @Override
    public void setUp() throws Exception {
        server = new Server("password",8123);

        game =  new Game();
        entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        game.addEntity(entity);
        game.addPlayer(0, player);



        logger = Mockito.mock(FakeLogger.class);
        MegaMek.setLogger(logger);
    }

    @Override
    public void tearDown() throws Exception {
        server.die();
    }

    //Moved to GameLogic
    public void testSetGame() {
        server.setGame(game);
        assertEquals(game, entity.getGame());
        assertTrue(player.isGhost());
        assertEquals(game, server.getGame());
    }

    //Stayed in Server, should be deleted later
    public void testGetPlayer() {
        server.setGame(game);
        assertEquals(server.getPlayer(0), player);
    }

    //Moved to GameServer
    public void testReceivePlayerVersion() {
        server.setGame(game);
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = MegaMek.getMegaMekSHA256();
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
        Mockito.verify(logger, Mockito.times(1)).info("SUCCESS: Client/Server Version (" + MegaMek.VERSION + ") and Checksum ("
                + MegaMek.getMegaMekSHA256() + ") matched");
    }

    //Moved to GameServer
    public void testReceivePlayerNameExisting() throws NoSuchFieldException, IllegalAccessException {
        server.setGame(game);
        Field connectionsPending = Server.class.getDeclaredField("connectionsPending");
        connectionsPending.setAccessible(true);
        Socket s = new Socket();
        IConnection connection = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s, 0).getClass());
        Vector<IConnection> connectionspending = new Vector<>(4);
        connectionspending.addElement(connection);
        connectionsPending.set(server, connectionspending);

        assertEquals(server.getPendingConnection(0), connection);
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_NAME, "JohnDoe"));

        assertNull(server.getPendingConnection(0));
        assertFalse(player.isGhost());
        assertEquals(server.getConnection(0), connection);
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        String who = player.getName() + " connected from " + connection.getInetAddress();
        Mockito.verify(logger, Mockito.times(1)).info("s: player #" + 0 + ", " + who);
    }

    //Moved to GameServer
    public void testReceivePlayerNameNew() throws NoSuchFieldException, IllegalAccessException, UnknownHostException {
        server.setGame(game);

        Field connectionsPending = Server.class.getDeclaredField("connectionsPending");
        connectionsPending.setAccessible(true);
        Socket s = new Socket();
        IConnection connection = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s, 1).getClass());
        Mockito.when(connection.getId()).thenReturn(1);
        Vector<IConnection> connectionspending = new Vector<>(4);
        connectionspending.addElement(connection);
        connectionsPending.set(server, connectionspending);

        server.handle(1,new Packet(Packet.COMMAND_CLIENT_NAME, "NewPlayer"));

        assertNull(server.getPendingConnection(1));
        IPlayer newplayer = server.getPlayer(1);
        assertFalse(newplayer.isGhost());
        assertEquals(server.getConnection(1), connection);
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        InetAddress[] addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        for (InetAddress address : addresses) {
            Mockito.verify(logger, Mockito.times(1)).info("s: machine IP " + address.getHostAddress());
        }
        String who = newplayer.getName() + " connected from " + connection.getInetAddress();
        Mockito.verify(logger, Mockito.times(1)).info("s: player #" + 1 + ", " + who);

        assertEquals(newplayer.getColour(), PlayerColour.RED);
        assertEquals(newplayer.getCamoCategory(), Camouflage.COLOUR_CAMOUFLAGE);
        assertEquals(newplayer.getScore().getTotalScore(), 0);
    }

    //Moved to GameServer
    public void testReceivePlayerNameNewDuplicate() throws NoSuchFieldException, IllegalAccessException, UnknownHostException {
        server.setGame(game);
        Field connectionsPending = Server.class.getDeclaredField("connectionsPending");
        connectionsPending.setAccessible(true);
        Socket s = new Socket();
        IConnection connection = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s, 0).getClass());
        Vector<IConnection> connectionspending = new Vector<>(4);
        Socket s2 = new Socket();
        IConnection connection2 = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s2, 1).getClass());
        Mockito.when(connection2.getId()).thenReturn(1);
        connectionspending.addElement(connection);
        connectionspending.addElement(connection2);
        connectionsPending.set(server, connectionspending);

        server.handle(0,new Packet(Packet.COMMAND_CLIENT_NAME, "JohnDoe"));
        server.handle(1,new Packet(Packet.COMMAND_CLIENT_NAME, "JohnDoe"));

        IPlayer newplayer = server.getPlayer(1);
        assertEquals(newplayer.getName(), "JohnDoe.2");
    }

    //Moved to GameServer
    public void testReceivePlayerInfo() throws NoSuchFieldException, IllegalAccessException {
        server.setGame(game);

        player.setColour(PlayerColour.BROWN);
        player.setStartingPos(5);
        player.setTeam(3);
        player.setCamoCategory("cat");
        player.setCamoFileName("cat.file");
        player.setNbrMFCommand(1);
        player.setNbrMFVibra(1);
        player.setNbrMFActive(1);
        player.setNbrMFInferno(1);
        player.setConstantInitBonus(10);
        EloScore score = new EloScore();
        score.win(1000);
        score.win(500);
        player.setScore(score);

        server.handle(0,new Packet(Packet.COMMAND_PLAYER_UPDATE, player));

        IPlayer gameplayer = server.getPlayer(0);
        assertEquals(gameplayer.getColour(), player.getColour());
        assertEquals(gameplayer.getStartingPos(), player.getStartingPos());
        assertEquals(gameplayer.getTeam(), player.getTeam());
        assertEquals(gameplayer.getCamoCategory(), player.getCamoCategory());
        assertEquals(gameplayer.getCamoFileName(), player.getCamoFileName());
        assertEquals(gameplayer.getNbrMFConventional(), player.getNbrMFConventional());
        assertEquals(gameplayer.getNbrMFCommand(), player.getNbrMFCommand());
        assertEquals(gameplayer.getNbrMFVibra(), player.getNbrMFVibra());
        assertEquals(gameplayer.getNbrMFActive(), player.getNbrMFActive());
        assertEquals(gameplayer.getNbrMFInferno(), player.getNbrMFInferno());
        assertEquals(gameplayer.getConstantInitBonus(), player.getConstantInitBonus());
        assertEquals(gameplayer.getScore().getTotalScore(), 1150);
    }


    //Moved to GameLogic
    public void testVictoryFalse() throws NoSuchFieldException, IllegalAccessException {
        server.setGame(game);

        VictoryResult draw = Mockito.mock(VictoryResult.class);
        Mockito.when(draw.victory()).thenReturn(false);

        Victory vic = Mockito.mock(Victory.class);
        Mockito.when(vic.checkForVictory(game, game.getVictoryContext())).thenReturn(draw);
        Field victory = Game.class.getDeclaredField("victory");
        victory.setAccessible(true);
        victory.set(game, vic);

        assertFalse(server.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    //Moved to GameLogic
    public void testVictoryDraw() throws NoSuchFieldException, IllegalAccessException {
        server.setGame(game);

        VictoryResult draw = Mockito.mock(VictoryResult.class);
        Mockito.when(draw.isDraw()).thenReturn(true);
        Mockito.when(draw.victory()).thenReturn(true);

        Victory vic = Mockito.mock(Victory.class);
        Mockito.when(vic.checkForVictory(game, game.getVictoryContext())).thenReturn(draw);
        Field victory = Game.class.getDeclaredField("victory");
        victory.setAccessible(true);
        victory.set(game, vic);

        assertTrue(server.victory());
        assertEquals(game.getVictoryPlayerId(), IPlayer.PLAYER_NONE);
        assertEquals(game.getVictoryTeam(), IPlayer.TEAM_NONE);
    }

    //Moved to GameLogic
    public void testVictoryWon() throws NoSuchFieldException, IllegalAccessException {
        server.setGame(game);

        VictoryResult won = Mockito.mock(VictoryResult.class);
        Mockito.when(won.isDraw()).thenReturn(false);
        Mockito.when(won.victory()).thenReturn(true);
        Mockito.when(won.getWinningPlayer()).thenReturn(0);
        Mockito.when(won.getWinningTeam()).thenReturn(1);

        Victory vic = Mockito.mock(Victory.class);
        Mockito.when(vic.checkForVictory(game, game.getVictoryContext())).thenReturn(won);
        Field victory = Game.class.getDeclaredField("victory");
        victory.setAccessible(true);
        victory.set(game, vic);

        assertTrue(server.victory());
        assertEquals(game.getVictoryPlayerId(), 0);
        assertEquals(game.getVictoryTeam(), 1);
    }


}