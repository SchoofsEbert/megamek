package megamek.server;

import junit.framework.TestCase;
import megamek.common.*;
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

        game =  Mockito.mock(Game.class);
        entity = new BipedMech();
        player = new Player(0, "JohnDoe");
        List<Entity> entities = new CopyOnWriteArrayList<>(new BipedMech[]{entity});
        Vector<IPlayer> players = new Vector<IPlayer>();
        players.addElement(player);
        Vector<AttackHandler> attacks = new Vector<AttackHandler>();
        Mockito.when(game.getEntities()).thenReturn(entities.iterator());
        Mockito.when(game.getOptions()).thenReturn(new GameOptions());
        Mockito.when(game.getPlayers()).thenReturn(players.elements());
        Mockito.when(game.getAttacks()).thenReturn(attacks.elements());
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
}