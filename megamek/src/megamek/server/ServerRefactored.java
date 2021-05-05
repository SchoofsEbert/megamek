package megamek.server;

import megamek.MegaMek;
import megamek.common.IGame;
import megamek.common.net.ConnectionFactory;
import megamek.common.net.IConnection;
import megamek.server.commands.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

abstract public class ServerRefactored implements Runnable{
    // listens for and connects players
    private Thread connector;

    public ServerRefactored(String password, int port) throws IOException {
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
    public ServerRefactored(String password, int port, boolean registerWithServerBrowser,
                  String metaServerUrl) throws IOException {
        // TODO IMPLEMENT
    }

    /**
     * Listen for incoming clients.
     */
    public void run() {
        // TODO IMPLEMENT
    }
}
