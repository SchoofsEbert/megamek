package megamek.server;

import megamek.common.*;
import megamek.common.event.GameListener;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;

import java.io.IOException;
import java.util.*;

public class GameServer extends ServerRefactored{
    private GameLogic gamelogic;

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


}
