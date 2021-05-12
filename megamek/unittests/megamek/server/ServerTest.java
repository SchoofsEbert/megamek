package megamek.server;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.client.ui.swing.boardview.FieldofFireSprite;
import megamek.common.*;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.logging.FakeLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.ConnectionFactory;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import megamek.common.options.GameOptions;
import megamek.common.weapons.ACAPHandler;
import megamek.common.weapons.AttackHandler;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.net.Socket;
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

    public void testSetGame() {
        server.setGame(game);
        assertEquals(game, entity.getGame());
        assertTrue(player.isGhost());
        assertEquals(game, server.getGame());
    }

    public void testGetPlayer() {
        server.setGame(game);
        assertEquals(server.getPlayer(0), player);
    }

    public void testReceivePlayerVersion() {
        server.setGame(game);
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = MegaMek.getMegaMekSHA256();
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
        Mockito.verify(logger, Mockito.times(1)).info("SUCCESS: Client/Server Version (" + MegaMek.VERSION + ") and Checksum ("
                + MegaMek.getMegaMekSHA256() + ") matched");
    }

    public void testReceivePlayerName() throws NoSuchFieldException, IllegalAccessException {
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
}