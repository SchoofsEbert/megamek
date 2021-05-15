package megamek.server;

import megamek.MegaMek;
import megamek.common.*;
import megamek.common.event.GameListener;
import megamek.common.net.IConnection;
import megamek.common.net.Packet;
import megamek.common.preference.PreferenceManager;
import megamek.common.weapons.AttackHandler;
import megamek.common.weapons.WeaponHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        this(password, port, registerWithServerBrowser, metaServerUrl);
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
    void receivePlayerVersion(Packet packet, int connId) {
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

    /**
     * Receives a player name, sent from a pending connection, and connects that
     * connection.
     */
    //TODO once refactored, should be set to private
    void receivePlayerName(Packet packet, int connId) {
        final IConnection conn = server.getPendingConnection(connId);
        String name = (String) packet.getObject(0);
        boolean returning = false;

        // this had better be from a pending connection
        if (conn == null) {

            MegaMek.getLogger().warning("Got a client name from a non-pending connection");
            return;
        }

        // check if they're connecting with the same name as a ghost player
        for (Enumeration<IPlayer> i = gamelogic.getGame().getPlayers(); i.hasMoreElements(); ) {
            IPlayer player = i.nextElement();
            if (player.getName().equals(name)) {
                if (player.isGhost()) {

                    returning = true;
                    player.setGhost(false);
                    // switch id
                    connId = player.getId();
                    conn.setId(connId);
                }
            }
        }

        if (!returning) {
            // Check to avoid duplicate names...
            name = gamelogic.correctDupeName(name);
            server.sendToPending(connId, new Packet(Packet.COMMAND_SERVER_CORRECT_NAME, name));
        }

        // right, switch the connection into the "active" bin
        server.connectionsPending.removeElement(conn);
        server.connections.addElement(conn);
        server.connectionIds.put(conn.getId(), conn);

        // add and validate the player info
        if (!returning) {
            gamelogic.addNewPlayer(connId, name);
        }

        // if it is not the lounge phase, this player becomes an observer
        IPlayer player = getPlayer(connId);
        if ( (gamelogic.getGame().getPhase() != IGame.Phase.PHASE_LOUNGE) && (null != player)
                &&  (gamelogic.getGame().getEntitiesOwnedBy(player) < 1)) {
            player.setObserver(true);
        }

        // send the player the motd
        server.sendServerChat(connId, server.motd);

        // send info that the player has connected
        server.send(server.createPlayerConnectPacket(connId));

        // tell them their local playerId
        server.send(connId, new Packet(Packet.COMMAND_LOCAL_PN, connId));

        // send current gameserver.getGame() info
        server.sendCurrentInfo(connId);

        final boolean showIPAddressesInChat = PreferenceManager.getClientPreferences().getShowIPAddressesInChat();

        try {
            InetAddress[] addresses = InetAddress.getAllByName(InetAddress
                    .getLocalHost().getHostName());
            for (InetAddress address : addresses) {
                MegaMek.getLogger().info("s: machine IP " + address.getHostAddress());
                if (showIPAddressesInChat) {
                    server.sendServerChat(connId,
                            "Machine IP is " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            // oh well.
        }

        MegaMek.getLogger().info("s: listening on port " + server.serverSocket.getLocalPort());
        if (showIPAddressesInChat) {
            // Send the port we're listening on. Only useful for the player
            // on the server machine to check.
            server.sendServerChat(connId,
                    "Listening on port " + server.serverSocket.getLocalPort());
        }

        // Get the player *again*, because they may have disconnected.
        player = getPlayer(connId);
        if (null != player) {
            String who = player.getName() + " connected from " + server.getClient(connId).getInetAddress();
            MegaMek.getLogger().info("s: player #" + connId + ", " + who);
            if (showIPAddressesInChat) {
                server.sendServerChat(who);
            }

        } // Found the player

    }

    /**
     * Allow the player to set whatever parameters he is able to
     */
    //TODO once refactored, should be set to private
    void receivePlayerInfo(Packet packet, int connId) {
        IPlayer player = (IPlayer) packet.getObject(0);
        IPlayer gamePlayer = gamelogic.getGame().getPlayer(connId);
        if (null != gamePlayer) { //TODO INTER: ONLY GAME
            if (gamePlayer.getConstantInitBonus()
                != player.getConstantInitBonus()) {
            server.sendServerChat("Player " + gamePlayer.getName()
                    + " changed their initiative bonus from "
                    + gamePlayer.getConstantInitBonus()
                    + " to " + player.getConstantInitBonus() + ".");
            }
            gamePlayer.update(player);
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

    public IPlayer addNewPlayer(int connId, String name) {
        return gamelogic.addNewPlayer(connId, name);
    }

    /**
     * Validates the player info.
     */
    public void validatePlayerInfo(int playerId) {
        gamelogic.validatePlayerInfo(playerId);
    }


}
