package megamek.server;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.client.ui.swing.util.PlayerColour;
import megamek.common.*;
import megamek.common.icons.Camouflage;
import megamek.common.logging.FakeLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.ConnectionFactory;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class GameServerTest extends TestCase {
    Server server;
    GameServer gameserver;
    Game game;
    //BipedMech entity;
    Player player;
    MMLogger logger;

    @Override
    public void setUp() throws Exception {
        server = new Server("password", 8123);

        game = new Game();
        //entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        game.addPlayer(0, player);
        //game.addEntity(entity);

        logger = Mockito.mock(FakeLogger.class);
        MegaMek.setLogger(logger);

        gameserver = new GameServer("password", 8123, server);

        gameserver.setGame(game);
    }

    @Override
    public void tearDown() throws Exception {
        server.die();
    }

    public void testReceivePlayerVersionCorrect() {
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = MegaMek.getMegaMekSHA256();
        gameserver.receivePlayerVersion(new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData), 0);
        Mockito.verify(logger, Mockito.times(1)).info("SUCCESS: Client/Server Version (" + MegaMek.VERSION + ") and Checksum ("
                + MegaMek.getMegaMekSHA256() + ") matched");
    }

    public void testReceivePlayerVersionMismatch() {
        Object[] versionData = new Object[2];
        versionData[0] = "Faulty";
        versionData[1] = MegaMek.getMegaMekSHA256();
        gameserver.receivePlayerVersion(new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData), 0);
        Mockito.verify(logger, Mockito.times(1)).error("Client/Server version Mismatch -- Client: Faulty Server: " + MegaMek.VERSION);
    }

    public void testReceivePlayerVersionChecksumMismatch() {
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = "Faulty";
        gameserver.receivePlayerVersion(new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData), 0);
        Mockito.verify(logger, Mockito.times(1)).error("Client/Server checksum Mismatch -- Client: Faulty Server: " + MegaMek.getMegaMekSHA256());
    }

    private ArrayList<IConnection> sendPlayerNames(List<Integer> connIds, List<String> names) throws NoSuchFieldException, IllegalAccessException {
        if (connIds.size() != names.size()) {
            fail();
        }
        Field connectionsPending = Server.class.getDeclaredField("connectionsPending");
        connectionsPending.setAccessible(true);
        Socket s = new Socket();
        ArrayList<IConnection> connections = new ArrayList<>(4);
        for (int connId : connIds) {
            IConnection connection = Mockito.mock(ConnectionFactory.getInstance().createServerConnection(s, connId).getClass());
            Mockito.when(connection.getId()).thenReturn(connId);
            connections.add(connection);
        }
        connectionsPending.set(server, new Vector<>(connections));
        for (int i = 0; i < connIds.size(); i++) {
            int connId = connIds.get(i);
            assertEquals(server.getPendingConnection(connId), connections.get(i));
            gameserver.receivePlayerName(new Packet(Packet.COMMAND_CLIENT_NAME, names.get(i)), connId);
        }
        return connections;
    }

    public void testReceivePlayerNameExisting() throws NoSuchFieldException, IllegalAccessException {
        IConnection connection = sendPlayerNames(List.of(0), List.of("JohnDoe")).get(0);

        assertNull(server.getPendingConnection(0));
        assertFalse(player.isGhost());
        assertEquals(server.getConnection(0), connection);
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        Mockito.verify(logger, Mockito.times(1)).info("s: listening on port " + server.getPort());
        String who = player.getName() + " connected from " + connection.getInetAddress();
        Mockito.verify(logger, Mockito.times(1)).info("s: player #" + 0 + ", " + who);
    }

    public void testReceivePlayerNameNew() throws NoSuchFieldException, IllegalAccessException, UnknownHostException {
        IConnection connection = sendPlayerNames(List.of(1), List.of("NewPlayer")).get(0);

        assertNull(server.getPendingConnection(1));
        IPlayer newplayer = gameserver.getPlayer(1);
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
    public void testReceivePlayerNameNewDuplicate() throws NoSuchFieldException, IllegalAccessException {
        sendPlayerNames(List.of(0, 1), List.of("JohnDoe", "JohnDoe"));

        IPlayer player = gameserver.getPlayer(0);
        assertEquals(player.getName(), "JohnDoe");
        IPlayer newplayer = gameserver.getPlayer(1);
        assertEquals(newplayer.getName(), "JohnDoe.2");
    }

    public void testReceivePlayerInfo() {
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

        gameserver.receivePlayerInfo(new Packet(Packet.COMMAND_PLAYER_UPDATE, player), 0);

        IPlayer gameplayer = gameserver.getPlayer(0);
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
}



