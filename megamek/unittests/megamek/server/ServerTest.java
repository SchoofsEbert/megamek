package megamek.server;

import junit.framework.TestCase;
import megamek.MegaMek;
import megamek.common.*;
import megamek.common.logging.DefaultMmLogger;
import megamek.common.logging.MMLogger;
import megamek.common.net.Packet;
import megamek.common.options.GameOptions;
import megamek.common.weapons.ACAPHandler;
import megamek.common.weapons.AttackHandler;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerTest extends TestCase {
    Server server;
    Game game;
    BipedMech entity;
    Player player;

    @Override
    public void setUp() throws Exception {
        server = new Server("password",8123);

        game =  new Game();
        entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        game.addEntity(entity);
        game.addPlayer(0, player);
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

    public void testReceivePlayerVersion() {
        server.setGame(game);
        Object[] versionData = new Object[2];
        versionData[0] = MegaMek.VERSION;
        versionData[1] = MegaMek.getMegaMekSHA256();
        server.handle(0,new Packet(Packet.COMMAND_CLIENT_VERSIONS, versionData));
    }
}