package megamek.server;

import megamek.MegaMek;
import megamek.common.*;
import megamek.common.event.GameListener;
import megamek.common.net.Packet;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;

import java.io.IOException;
import java.util.*;

public class GameServer extends ServerRefactored{
    private GameLogic gamelogic;

    //TODO, once refactoring is done, all connections with server should be removed, because the method calls to server are on their place
    //AND the following constructors should be deleted!
    private Server server;

    public GameServer(String password, int port, Server server) throws IOException {
        this(password, port, false, "", server);
    }
    public GameServer(String password, int port, boolean registerWithServerBrowser,
                      String metaServerUrl, Server server) throws IOException {
        super(password, port, registerWithServerBrowser, metaServerUrl);
        // TODO IMPLEMENT
        gamelogic = new GameLogic();
        this.server = server;
    }

    //END constructors to be deleted

    public GameServer(String password, int port) throws IOException {
        this(password, port, false, "");
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
        gamelogic = new GameLogic();
    }

    //TODO once refactored, should be set to private
    public void receivePlayerVersion(Packet packet, int connId) {
        String version = (String) packet.getObject(0);
        String clientChecksum = (String) packet.getObject(1);
        String serverChecksum = MegaMek.getMegaMekSHA256();
        StringBuilder buf = new StringBuilder();
        boolean needs = false;
        if (!version.equals(MegaMek.VERSION)) {
            buf.append("Client/Server version mismatch. Server reports: ").append(MegaMek.VERSION)
                    .append(", Client reports: ").append(version);
            MegaMek.getLogger().error("Client/Server Version Mismatch -- Client: "
                    + version + " Server: " + MegaMek.VERSION);
            needs = true;
        }
        // print a message indicating client doesn't have jar file
        if (clientChecksum == null) {
            if (!version.equals(MegaMek.VERSION)) {
                buf.append(System.lineSeparator()).append(System.lineSeparator());
            }
            buf.append("Client Checksum is null. Client may not have a jar file");
            MegaMek.getLogger().info("Client does not have a jar file");
            needs = true;
            // print message indicating server doesn't have jar file
        } else if (serverChecksum == null) {
            if (!version.equals(MegaMek.VERSION)) {
                buf.append(System.lineSeparator()).append(System.lineSeparator());
            }
            buf.append("Server Checksum is null. Server may not have a jar file");
            MegaMek.getLogger().info("Server does not have a jar file");
            needs = true;
            // print message indicating a client/server checksum mismatch
        } else if (!clientChecksum.equals(serverChecksum)) {
            if (!version.equals(MegaMek.VERSION)) {
                buf.append(System.lineSeparator());
                buf.append(System.lineSeparator());
            }
            buf.append("Client/Server checksum mismatch. Server reports: ").append(serverChecksum)
                    .append(", Client reports: ").append(clientChecksum);
            MegaMek.getLogger().error("Client/Server Checksum Mismatch -- Client: " + clientChecksum
                    + " Server: " + serverChecksum);

            needs = true;
        }

        // Now, if we need to, send message!
        if (needs) {
            IPlayer player = getPlayer(connId);
            if (null != player) {
                //TODO this should be just sendServerChat once refactoring is done and the method is moved to ServerRefactored
                server.sendServerChat("For " + player.getName() + " Server reports:"
                        + System.lineSeparator()
                        + buf.toString());
            }
        } else {
            MegaMek.getLogger().info("SUCCESS: Client/Server Version (" + version + ") and Checksum ("
                    + clientChecksum + ") matched");
        }
    }





    ////////TODO once refactored, these shortcut methods should be deleted.
    /**
     * Shortcut to gamelogic.getGame().getPlayer(id)
     */
    public IPlayer getPlayer(int id) {
        return gamelogic.getGame().getPlayer(id);
    }

    /**
     * Shortcut to gamelogic.getGame()
     */
    public IGame getGame() {
        return gamelogic.getGame();
    }

    /**
     * Shortcut to gamelogic.setGame()
     */
    public void setGame(IGame game) {
        gamelogic.setGame(game);
    }


}
