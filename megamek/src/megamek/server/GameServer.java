package megamek.server;

import megamek.common.*;
import megamek.common.event.GameListener;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;

import java.io.IOException;
import java.util.*;

public class GameServer extends ServerRefactored{
    private GameLogic gamelogic;

    private IGame game = new Game();


    public GameServer(String password, int port) throws IOException {
        super(password, port);
    }

    /**
     * Construct a new GameHost and begin listening for incoming clients.
     *
     *
     * @param password                  the <code>String</code> that is set as a password
     * @param port                      the <code>int</code> value that specifies the port that is
     *                                  used
     * @param registerWithServerBrowser a <code>boolean</code> indicating whether we should register
     *                                  with the master server browser on megamek.info
     */
    public GameServer(String password, int port, boolean registerWithServerBrowser,
                            String metaServerUrl) throws IOException {
        super(password, port, registerWithServerBrowser, metaServerUrl);
        // TODO IMPLEMENT
    }

    /**
     * Sets the game for this server. Restores any transient fields, and sets
     * all players as ghosts. This should only be called during server
     * initialization before any players have connected.
     */

    /**
     * Returns the current game object
     */
    public IGame getGame() {
        return game;
    }

    public void setGame(IGame g) {
        // game listeners are transient so we need to save and restore them
        Vector<GameListener> gameListenersClone = new Vector<>(getGame().getGameListeners());

        game = g;

        for (GameListener listener : gameListenersClone) {
            getGame().addGameListener(listener);
        }

        List<Integer> orphanEntities = new ArrayList<>();

        // reattach the transient fields and ghost the players
        for (Iterator<Entity> e = game.getEntities(); e.hasNext(); ) {
            Entity ent = e.next();
            ent.setGame(game);

            if(ent.getOwner() == null) {
                orphanEntities.add(ent.getId());
                continue;
            }

            if (ent instanceof Mech) {
                ((Mech) ent).setBAGrabBars();
                ((Mech) ent).setProtomechClampMounts();
            }
            if (ent instanceof Tank) {
                ((Tank) ent).setBAGrabBars();
            }
        }

        game.removeEntities(orphanEntities, IEntityRemovalConditions.REMOVE_UNKNOWN);

        game.setOutOfGameEntitiesVector(game.getOutOfGameEntitiesVector());
        for (Enumeration<IPlayer> e = game.getPlayers(); e.hasMoreElements(); ) {
            IPlayer p = e.nextElement();
            p.setGame(game);
            p.setGhost(true);
        }
        // might need to restore weapon type for some attacks that take multiple
        // turns (like artillery)
        for (Enumeration<AttackHandler> a = game.getAttacks(); a
                .hasMoreElements(); ) {
            AttackHandler handler = a.nextElement();
            if (handler instanceof WeaponHandler) {
                ((WeaponHandler) handler).restore();
            }
        }

    }
}
